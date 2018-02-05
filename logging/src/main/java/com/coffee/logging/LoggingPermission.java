package com.coffee.logging;

import java.security.BasicPermission;

public class LoggingPermission extends BasicPermission {
	
	private static final long serialVersionUID = -6316698483890573329L;

	public LoggingPermission(String name, String actions) throws IllegalArgumentException {
		super(name);
		if(!name.equals("control")) {
			throw new IllegalArgumentException("name: " +name);
		}
		if(actions != null && actions.length() > 0) {
			throw new IllegalArgumentException("actions: " + actions);
		}
	}

	

}
