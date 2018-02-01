package com.coffee.logging;
/**
 * A Filter can be used to provide fine grain control over
 * wath is logged, beyond the control provided by log levels.
 * Each Logger and each Handler can have a filter associated with it.
 * The Logger or Handler will call the isLoggable method to check
 * if a given LogRecord should be published. If isLoggable returns
 * false, the LogRecord will be discarded.
 * @author Administrator
 *
 */
public interface Filter {
	/**
	 * Check if a given log record should be published.
	 */
	public boolean isLoggable(LogRecord record);
}
