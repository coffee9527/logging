package com.coffee.logging;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import sun.misc.JavaLangAccess;
import sun.misc.SharedSecrets;

public class LogRecord implements java.io.Serializable{
	private static final AtomicLong globalSequenceNumber
		= new AtomicLong(0);
	
	private static final int MIN_SEQUENTIAL_THREAD_ID = Integer.MAX_VALUE / 2;
	
	private static final AtomicInteger nextThreadId 
		= new AtomicInteger(MIN_SEQUENTIAL_THREAD_ID);
	
	private static final ThreadLocal<Integer> threadIds = new ThreadLocal<Integer>();
	
	private Level level;
	
	private long sequenceNumber;
	
	private String sourceClassName;
	
	private String sourceMethodName;
	
	private String message;
	
	private int threadID;
	
	private long millis;
	
	private Throwable thrown;
	
	private String loggerName;
	
	private String resourceBundleName;
	
	private transient boolean needToInferCaller;
	private transient Object parameters[];
	private transient ResourceBundle resourceBundle;
	
	private int defaultThreadID() {
		long tid = Thread.currentThread().getId();
		if(tid < MIN_SEQUENTIAL_THREAD_ID) {
			return (int)tid;
		} else {
			Integer id = threadIds.get();
			if(id == null) {
				id = nextThreadId.getAndIncrement();
				threadIds.set(id);
			}
			return id;
		}
	}
	
	public LogRecord(Level level, String msg) {
		level.getClass();
		this.level = level;
		message = msg;
		
		sequenceNumber = globalSequenceNumber.getAndIncrement();
		threadID = defaultThreadID();
		millis = System.currentTimeMillis();
		needToInferCaller = true;
	}
	
	public String getLoggerName() {
		return loggerName;
	}
	
	public void setLoggerName(String name) {
		loggerName = name;
	}
	
	public ResourceBundle getResourceBundle() {
		return resourceBundle;
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		resourceBundle = bundle;
	}
	
	public String getResourceBundleName() {
		return resourceBundleName;
	}
	
	public void setResourceBundleName(String name) {
		resourceBundleName = name;
	}
	
	public Level getLevel() {
        return level;
    }

    /**
     * Set the logging message level, for example Level.SEVERE.
     * @param level the logging message level
     */
    public void setLevel(Level level) {
        if (level == null) {
            throw new NullPointerException();
        }
        this.level = level;
    }

    /**
     * Get the sequence number.
     * <p>
     * Sequence numbers are normally assigned in the LogRecord
     * constructor, which assigns unique sequence numbers to
     * each new LogRecord in increasing order.
     * @return the sequence number
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Set the sequence number.
     * <p>
     * Sequence numbers are normally assigned in the LogRecord constructor,
     * so it should not normally be necessary to use this method.
     * @param seq the sequence number
     */
    public void setSequenceNumber(long seq) {
        sequenceNumber = seq;
    }
    
    public String getSourceClassName() {
    	if(needToInferCaller) {
    		inferCaller();
    	}
    	return sourceClassName;
    }
    
    public void setSourceClassName(String sourceClassName) {
    	this.sourceClassName = sourceClassName;
    	needToInferCaller = false;
    }
    
    public String getSourceMethodName() {
    	if(needToInferCaller) {
    		inferCaller();
    	}
    	return sourceMethodName;
    }
    
    public void setSourceMethodName(String sourceMethodName) {
    	this.sourceMethodName = sourceMethodName;
    	needToInferCaller = false;
    }
    
    public String getMessage() {
    	return message;
    }
    
    public void setMessage(String message) {
    	this.message = message;
    }
    
    public Object[] getParameters() {
    	return parameters;
    }
    
    public void setParameters(Object parameters[]) {
    	this.parameters = parameters;
    }
    
    public int getThreadID() {
        return threadID;
    }

    /**
     * Set an identifier for the thread where the message originated.
     * @param threadID  the thread ID
     */
    public void setThreadID(int threadID) {
        this.threadID = threadID;
    }

    /**
     * Get event time in milliseconds since 1970.
     *
     * @return event time in millis since 1970
     */
    public long getMillis() {
        return millis;
    }

    /**
     * Set event time.
     *
     * @param millis event time in millis since 1970
     */
    public void setMillis(long millis) {
        this.millis = millis;
    }
    
    public Throwable getThrown() {
        return thrown;
    }

    /**
     * Set a throwable associated with the log event.
     *
     * @param thrown  a throwable (may be null)
     */
    public void setThrown(Throwable thrown) {
        this.thrown = thrown;
    }

    private static final long serialVersionUID = 5372048053134512534L;
    
    private void writeObject(ObjectOutputStream out) throws IOException {
    	out.defaultWriteObject();
    	
    	out.writeByte(1);
    	out.writeByte(0);
    	if(parameters == null) {
    		out.writeInt(-1);
    		return;
    	}
    	out.writeInt(parameters.length);
    	for(int i=0; i<parameters.length; i++) {
    		if(parameters[i] == null) {
    			out.writeObject(null);
    		} else {
    			out.writeObject(parameters[i].toString());
    		}
    	}
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    	in.defaultReadObject();
    	
    	byte major = in.readByte();
    	byte minor = in.readByte();
    	if(major != 1) {
    		throw new IOException("LogRecord: bad version:" + major + "." + minor);
    	}
    	int len = in.readInt();
    	if(len <-1) {
    		throw new NegativeArraySizeException();
    	}else if(len == -1) {
    		parameters = null;
    	}else if(len < 255) {
    		parameters = new Object[len];
    		for(int i=0; i<parameters.length; i++) {
    			parameters[i] = in.readObject();
    		}
    	}else {
    		List<Object> params = new ArrayList<Object>(Math.min(len, 1024));
    		for(int i=0; i<len; i++) {
    			params.add(in.readObject());
    		}
    		parameters = params.toArray(new Object[params.size()]);
    	}
    	
    	if(resourceBundleName != null) {
    		try {
    			final ResourceBundle bundle =
    					ResourceBundle.getBundle(resourceBundleName, 
    							Locale.getDefault(),
    							ClassLoader.getSystemClassLoader());
    			resourceBundle = bundle;
    		}catch (MissingResourceException ex) {
    			resourceBundle = null;
    		}
    	}
    	needToInferCaller = false;
    }
    
    private void inferCaller() {
    	needToInferCaller = false;
    	JavaLangAccess access = SharedSecrets.getJavaLangAccess();
    	Throwable throwable = new Throwable();
    	int depth = access.getStackTraceDepth(throwable);
    	
    	boolean lookingForLogger = true;
    	for(int ix=0; ix<depth; ix++) {
    		StackTraceElement frame = 
    				access.getStackTraceElement(throwable, ix);
    		String cname = frame.getClassName();
    		boolean isLoggerImpl = isLoggerImplFrame(cname);
    		if(lookingForLogger) {
    			if(isLoggerImpl) {
    				lookingForLogger = false;
    			}
    		}else {
    			if(!isLoggerImpl) {
    				if(!cname.startsWith("java.lang.reflect.") && !cname.startsWith("sun.reflect.")) {
    					setSourceClassName(cname);
    					setSourceClassName(frame.getMethodName());
    					return;
    				}
    			}
    		}
    	}
    }
    
    private boolean isLoggerImplFrame(String cname) {
    	return (cname.equals("java.util.logging.Logger")) ||
    			cname.startsWith("java.util.logging.LoggingProxyImpl") ||
    			cname.startsWith("sun.util.logging.");
    }
}
