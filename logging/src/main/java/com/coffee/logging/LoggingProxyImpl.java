package com.coffee.logging;

import java.util.List;

import sun.util.logging.LoggingProxy;

public class LoggingProxyImpl implements LoggingProxy {
	static final LoggingProxy INSTANCE = new LoggingProxyImpl();
	
	private LoggingProxyImpl() {}

	public Object getLevel(Object logger) {
		return ((Logger)logger).getLevel();
	}

	public String getLevelName(Object level) {
		return ((Level)level).getLevelName();
	}

	public int getLevelValue(Object level) {
		return ((Level)level).intValue();
	}

	public Object getLogger(String name) {
		return Logger.getPlatformLogger(name);
	}

	public String getLoggerLevel(String loggerName) {
		return LogManager.getLoggingMXBean().getLoggerLevel(loggerName);
	}

	public List<String> getLoggerNames() {
		return LogManager.getLoggingMXBean().getLoggerNames();
	}

	public String getParentLoggerName(String loggerName) {
		return LogManager.getLoggingMXBean().getParentLoggerName(loggerName);
	}

	public String getProperty(String property) {
		return LogManager.getLogManager().getProperty(property);
	}

	public boolean isLoggable(Object logger, Object level) {
		return ((Logger)logger).isLoggable((Level)level);
	}

	public void log(Object logger, Object level, String msg) {
		((Logger)logger).log((Level)level, msg);
	}

	public void log(Object logger, Object level, String msg, Throwable t) {
		((Logger) logger).log((Level) level, msg, t);
	}

	public void log(Object logger, Object level, String msg, Object... params) {
		((Logger) logger).log((Level) level, msg, params);
	}

	public Object parseLevel(String levelName) {
		Level level = Level.findLevel(levelName);
		if(level == null) {
			throw new IllegalArgumentException("Unknow level \"" + levelName + "\"");
		}
		return level;
	}

	public void setLevel(Object logger, Object newLevel) {
		 ((Logger) logger).setLevel((Level) newLevel);
	}

	public void setLoggerLevel(String loggerName, String levelName) {
		LogManager.getLoggingMXBean().setLoggerLevel(loggerName, levelName);
	}

}
