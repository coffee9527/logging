package com.coffee.logging;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.sun.org.glassfish.external.statistics.annotations.Reset;

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
	
	class LoggerContext {
		
	}
	
	final class SystemLoggerContext extends LoggerContext {
		
	}
	
	private final class RootLogger extends Logger {
		
	}
}
