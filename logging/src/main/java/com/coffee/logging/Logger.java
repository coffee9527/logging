package com.coffee.logging;

import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

public class Logger {
	private static final Handler emptyHandlers[] = new Handler[0];
	private static final int offValue = Level.OFF.intValue();
	
	static final String SYSTEM_LOGGER_RB_NAME = "sun.util.logging.resources.logging";
	private static final class LoggerBundle {
		final String resourceBundleName;
		final ResourceBundle userBundle;
		private LoggerBundle(String resourceBundleName, ResourceBundle bundle) {
			this.resourceBundleName = resourceBundleName;
			this.userBundle = bundle;
		}
		boolean isSystemBundle() {
			return SYSTEM_LOGGER_RB_NAME.equals(resourceBundleName);
		}
		static LoggerBundle get(String name, ResourceBundle bundle) {
			if(name == null && bundle == null) {
				return NO_RESOURCE_BUNDLE;
			} else if(SYSTEM_LOGGER_RB_NAME.equals(name) && bundle == null) {
				return SYSTEM_BUNDLE;
			} else {
				return new LoggerBundle(name, bundle);
			}
		}
	}
	
	private static final LoggerBundle SYSTEM_BUNDLE = 
			new LoggerBundle(SYSTEM_LOGGER_RN_NAME, null);
	
	private static final LoggerBundle NO_RESOURCE_BUNDLE = 
			new LoggerBundle(null, null);
	private volatile LogManager manager;
	private String name;
	private final CopyOnWriteArrayList<Handler> handlers = 
			new CopyOnWriteArrayList<Handler>();
	private volatile LoggerBundle loggerBundle = NO_RESOURCE_BUNDLE;
	private volatile boolean useParentHandlers = true;
	private volatile Filter filter;
	private boolean anonymous;
	
	private ResourceBundle catalog;
	private String catalogName;
	private Locale catalogLocale;
	private static final Object treeLock = new Object();
	private volatile Logger parent;
	private ArrayList<LogManager.LoggerWeakRef> kids;
	private volatile Level levelObject;
	private volatile int levelValue;
	private WeakReference<ClassLoader> callersClassLoaderRef;
	private final boolean isSystemLogger;
	
	public static final String GLOBAL_LOGGER_NAME = "global";
	
	public static final Logger getGloabal() {
		LogManager.getLogManager();
		return global;
	}
	
	@Deprecated
	public static final Logger global = new Logger(GLOBAL_LOGGER_NAME);
	
	protected Logger(String name, String resourceBundleName) {
		this(name, resouceBundleName, null, LogManager.getLogManager(), false);
	}
	
	Logger(String name, String resouceBundleName, Class<?>  caller, LogManager manager, boolean isSystemLogger) {
		this.manager = manager;
		this.isSystemLogger = isSystemLogger;
		setupResourceInfo(resouceBundleName, caller);
		this.name = name;
		levelValue = Level.INFO.intValue();
	}
	
	private void setCallersClassLoaderRef(Class<?> caller) {
		ClassLoader callersClassLoader = ((caller != null)
										? caller.getClassLoader()
												: null);
		if(callersClassLoader != null) {
			this.callersClassLoaderRef = new WeakReference<ClassLoader>(callersClassLoader);
		}
	}
	
	private ClassLoader getCallersClassLoader() {
		return (callersClassLoaderRef ;!= null)
				? callersClassLoaderRef.get()
						: null;
	}
	
	private Logger(String name) {
		this.name = name;
		this.isSystemLogger = true;
		levelValue = Level.INFO.intValue();
	}
	
	void setLogManager(LogManager manager) {
		this.manager = manager;
	}
	
	private void checkPermission() throws SecurityException {
		if(!anonymous) {
			if(manager == null) {
				manager = LogManager.getLogManager();
			}
			manager.checkPermission();
		}
	}
	
	private static class SystemLoggerHelper {
		static boolean disableCallerCheck = getBooleanProperty("sun.util.logging.disableCallerCheck");
		private static boolean getBooleanProperty(final String key) {
			String s = AccessController.doPrivileged(new PrivilegedAction<String>() {
				public String run() {
					return System.getProperty(key);
				}
			});
			return Boolean.valueOf(s);
		}
	}
	
	private static Logger demandLogger(String name, String resourceBundleName, Class<?> caller) {
		LogManager manager = LogManager.getLogManager();
		SecurityManager sm = System.getSecurityManager();
		if(sm != null && !SystemLoggerHelper.disableCallerCheck) {
			if(caller.getClassLoader() == null) {
				return manager.demandSystemLogger(name, resourceBundleName);
			}
		}
		return manager.demandLogger(name, resourceBundleName, caller);
	}
	
	@CallerSensitive
	public static Logger getLogger(String name) {
		return demandLogger(name, null, Reflection.getCallerClass());
	}
	
	@CallerSensitive
	public static Logger getLogger(String name, String resourceBundleName) {
		Class<?> callerClass = Reflection.getCallerClass();
		Logger result = demandLogger(name, resourceBundleName, callerClass);
		result.setupResourceInfo(resourceBundleName, callerClass);
		return result;
	}
	
	static Logger getPlatformLogger(String name) {
		LogManager manager = LogManager.getLogManager();
		Logger result = manager.demandSystemLogger(name, SYSTEM_LOGGER_RB_NAME);
		return result;
	}
	
	public static Logger getAnonymousLogger() {
		return getAnonymousLogger(null);
	}
	
	@CallerSensitive
	public static Logger getAnonymousLogger(String resourceBundleName) {
		LogManager manager = LogManager.getLogManager();
		manager.drainLoggerRefQueueBounded();
		Logger result = new Logger(null, resourceBundleName,
									Reflection.getCallerClass(), manager, false);
		result.anonymous = true;
		Logger root = manager.getLogger("");
		result.doSetParent(root);
		return result;
	}
	
	public ResourceBundle getResourceBundle() {
		return findResourceBundle(getResourceBundleName, true);
	}
	
	public String getResourceBundleName() {
		return loggerBundle.resourceBundleName;
	}
	
	public void setFilter(Filter newFilter) throws SecurityException {
		checkPermission();
		filter = newFilter;
	}
	
	public Filter getFilter() {
		return filter;
	}
	
	public void log(LogRecord record) {
		if(!isLoggable(record.getLevel()) ) {
			return;
		}
		Filter theFilter = filter;
		if(theFilter != null && !theFilter.isLoggable(record)) {
			return;
		}
		
		Logger logger = this;
		while(logger != null) {
			final Handler[] loggerHandlers = isSystemLogger
					? logger.accessCheckedHandlers()
					: logger.getHandlers();
			
			for(Handler handler : loggerHandlers) {
				handler.publish(record);
			}
			
			final boolean useParentHdls = isSystemLogger
					? logger.useParentHandlers
					: logger.getUseParentHandlers();
			
			if(!useParentHdls) {
				break;
			}
			
			logger = isSystemLogger ? logger.parent : logger.getParent();
		}
	}
}
