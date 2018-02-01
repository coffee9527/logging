package com.coffee.logging;

import java.beans.PropertyChangeListener;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.WeakHashMap;

import sun.misc.JavaAWTAccess;
import sun.misc.SharedSecrets;

public class LogManager {
	private static final LogManager manager;
	
	private volatile Properties props = new Properties();
	private final static Level defaultLevel = Level.INFO;
	
	private final Map<Object, Integer> listenerMap = new HashMap<Object, Integer>();
	
	private final LoggerContext systemContext = new SystemLoggerContext();
	private final LoggerContext userContext = new LoggerContext();
	
	private volatile Logger rootLogger;
	
	private volatile boolean readPrimordialConfiguration;
	
	private boolean initializedGlobalHandlers = true;
	
	private boolean deathImminent;
	
	static {
		manager = AccessController.doPrivileged(new PrivilegedAction<LogManager>() {

			public LogManager run() {
				LogManager mgr = null;
				String cname = null;
				try {
					cname = System.getProperty("java.util.logging.manager");
					if(cname != null) {
						try {
							Class<?> clz = ClassLoader.getSystemClassLoader()
									.loadClass(cname);
							mgr = (LogManager)clz.newInstance();
						}catch(ClassNotFoundException ex) {
							Class<?> clz = Thread.currentThread()
									.getContextClassLoader().loadClass(cname);
							mgr = (LogManager)clz.newInstance();
						}
					}
				}catch(Exception ex) {
					System.err.println("Could not load Logmanager \"" + cname + "\"");
					ex.printStackTrace();
				}
				if(mgr == null) {
					mgr = new LogManager();
				}
				return mgr;
			}
			
		});
	}
	
	private class Cleaner extends Thread {
		private Cleaner() {
			this.setContextClassLoader(null);
		}
		
		@Override
		public void run() {
			LogManager mgr = manager;
			synchronized (LogManager.this) {
				deathImminent = true;
				initializedGlobalHandlers = true;
			}
			reset();
		}
	}
	
	protected LogManager() {
		this(checkSubclassPermissions());
	}
	
	protected LogManager(Void checked) {
		try {
			Runtime.getRuntime().addShutdownHook(new Cleaner());
		} catch (IllegalStateException e) {
			
		}
	}
	
	private static Void checkSubclassPermissions() {
		final SecurityManager sm = System.getSecurityManager();
		if(sm != null) {
			sm.checkPermission(new RuntimePermission("shutdownHooks"));
			sm.checkPermission(new RuntimePermission("setContextClassLoader"));
		}
		return null;
	}
	
	private boolean initializedCalled = false;
	private volatile boolean initializationDone = false;
	final void ensureLogMangerInitialized() {
		final LogManager owner = this;
		if(initializationDone || owner != manager) {
			return;
		}
		
		synchronized(this) {
			final boolean isRecursiveInitialization = (initializedCalled == true);
			assert initializedCalled || !initializationDone
					:"Initialization can't be done if initialized has not been called!";
			if(isRecursiveInitialization || initializationDone) {
				return;
			}
			initializedCalled = true;
			try {
				AccessController.doPrivileged(new PrivilegedAction<Object>() {
					public Object run() {
						assert rootLogger == null;
						assert initializedCalled && !initializationDone;
						
						owner.readPrimordialConfiguration();
						owner.rootLogger = owner.new RootLogger();
						owner.addLogger(owner.rootLogger);
						if(!owner.rootLogger.isLevelInitialized()) {
							owner.rootLogger.setLevel(defaultLevel);
						}
						@SupperssWarnings("deprecation")
						final Logger global = Logger.global;
						
						owner.addLogger(global);
						return null;
					}
				});
			} finally {
				initializationDone = true;
			}
		}
	}
	
	public static LogManager getLogManager() {
		if(manager != null) {
			manager.ensureLogMangerInitialized();
		}
		return manager;
	}
	
	private void readPrimordialConfiguration() {
		if(!readPrimordialConfiguration) {
			synchronized(this) {
				if(!readPrimordialConfiguration) {
					if(System.out == null) {
						return;
					}
					readPrimordialConfiguration = true;
					try {
						AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
							@Override
							public void run() throws Exception {
								readConfiguration();
								sun.util.logging.PlatformLogger.redirectPlatformLoggers();
								return null;
							}
						});
					}catch(Exception ex) {
						assert false : "Exception raised while reading logging configuration: " + ex;
					}
				}
			}
		}
	}
	@Deprecated
	public void addPropertyChangeListener(PropertyChangeListener l) throws SecurityException {
		PropertyChangeListener listener = Objects.requireNonNull(l);
		checkPermission();
		synchronized (listenerMap) {
			Integer value = listenerMap.get(listener);
			value = (value == null) ? 1 : (value + 1);
			listenerMap.put(listener, value);
		}
	}
	
	@Deprecated
	public void removePropertyChangeListener(PropertyChangeListener l) throws SecurityException {
		checkPermission();
		if(l != null) {
			PropertyChangeListener listener = l;
			synchronized (listenerMap) {
				Integer value = listenerMap.get(listener);
				if(value != null) {
					int i = value.intValue();
					if(i == 1) {
						listenerMap.remove(listener);
					} else {
						assert i > 1;
						listenerMap.put(listener, i-1);
					}
				}
			}
		}
	}
	
	private WeakHashMap<Object, LoggerContext> contextsMap = null;
	
	private LoggerContext getUserContext() {
		LoggerContext context = null;
		
		SecurityManager sm = System.getSecurityManager();
		JavaAWTAccess javaAwtAccess = SharedSecrets.getJavaAWTAccess();
		if(sm != null && javaAwtAccess != null) {
			final Object ecx = javaAwtAccess.getAppletContext();
			if(ecx != null) {
				synchronized (javaAwtAccess) {
					if(contextsMap == null) {
						contextsMap = new WeakHashMap<Object, LogManager.LoggerContext>();
					}
					context = contextsMap.get(ecx);
					if(context == null) {
						context = new LoggerContext();
						contextsMap.put(ecx, context);
					}
				}
			}
		}
		return context != null ? context : userContext;
	}
	
	final LoggerContext getSystemContext() {
		return systemContext;
	}
	
	private List<loggerContext> contexts() {
		List<LoggerContext> cxs = new ArrayList<LoggerContext>();
		cxs.add(getSystemContext());
		cxs.add(getUserContext());
		return cxs;
	}
	
	Logger demandLogger(String name, String resourceBundleName, Class<?> caller) {
		Logger result = getLogger(name);
		if(result == null) {
			Logger newLogger = new Logger(name, resourceBundleName, caller, this, false);
		}
	}
	
	class LoggerContext {
		
	}
	
	final class SystemLoggerContext extends LoggerContext {
		
	}
	
	private final class RootLogger extends Logger {
		
	}
}
