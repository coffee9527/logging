package com.coffee.logging;

import java.util.logging.LogManager;

public abstract class Handler {
	private static final int offValue = Level.OFF.intValue();
	private final LogManager manager = LogManager.getLogManager();
	
	private volatile Filter filter;
}
