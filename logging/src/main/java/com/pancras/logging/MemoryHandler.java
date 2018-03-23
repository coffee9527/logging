package com.pancras.logging;


public class MemoryHandler extends Handler {
    private final static int DEFAULT_SIZE = 1000;
    private volatile Level pushLevel;
    private int size;
    private Handler target;
    private LogRecord buffer[];
    int start, count;

    // Private method to configure a MemoryHandler from LogManager
    // properties and/or default values as specified in the class
    // javadoc.
    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();

        pushLevel = manager.getLevelProperty(cname +".push", Level.SEVERE);
        size = manager.getIntProperty(cname + ".size", DEFAULT_SIZE);
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        setLevel(manager.getLevelProperty(cname +".level", Level.ALL));
        setFilter(manager.getFilterProperty(cname +".filter", null));
        setFormatter(manager.getFormatterProperty(cname +".formatter", new SimpleFormatter()));
    }

    /**
     * Create a <tt>MemoryHandler</tt> and configure it based on
     * <tt>LogManager</tt> configuration properties.
     */
    public MemoryHandler() {
        sealed = false;
        configure();
        sealed = true;

        LogManager manager = LogManager.getLogManager();
        String handlerName = getClass().getName();
        String targetName = manager.getProperty(handlerName+".target");
        if (targetName == null) {
            throw new RuntimeException("The handler " + handlerName
                    + " does not specify a target");
        }
        Class<?> clz;
        try {
            clz = ClassLoader.getSystemClassLoader().loadClass(targetName);
            target = (Handler) clz.newInstance();
        } catch (ClassNotFoundException  e) {
            throw new RuntimeException("MemoryHandler can't load handler target \"" + targetName + "\"" , e);
        } catch (InstantiationException e) {
        	throw new RuntimeException("MemoryHandler can't load handler target \"" + targetName + "\"" , e);
        }catch(IllegalAccessException e) {
        	throw new RuntimeException("MemoryHandler can't load handler target \"" + targetName + "\"" , e);
        }
        init();
    }

    // Initialize.  Size is a count of LogRecords.
    private void init() {
        buffer = new LogRecord[size];
        start = 0;
        count = 0;
    }

    /**
     * Create a <tt>MemoryHandler</tt>.
     * <p>
     * The <tt>MemoryHandler</tt> is configured based on <tt>LogManager</tt>
     * properties (or their default values) except that the given <tt>pushLevel</tt>
     * argument and buffer size argument are used.
     *
     * @param target  the Handler to which to publish output.
     * @param size    the number of log records to buffer (must be greater than zero)
     * @param pushLevel  message level to push on
     *
     * @throws IllegalArgumentException if {@code size is <= 0}
     */
    public MemoryHandler(Handler target, int size, Level pushLevel) {
        if (target == null || pushLevel == null) {
            throw new NullPointerException();
        }
        if (size <= 0) {
            throw new IllegalArgumentException();
        }
        sealed = false;
        configure();
        sealed = true;
        this.target = target;
        this.pushLevel = pushLevel;
        this.size = size;
        init();
    }

    /**
     * Store a <tt>LogRecord</tt> in an internal buffer.
     * <p>
     * If there is a <tt>Filter</tt>, its <tt>isLoggable</tt>
     * method is called to check if the given log record is loggable.
     * If not we return.  Otherwise the given record is copied into
     * an internal circular buffer.  Then the record's level property is
     * compared with the <tt>pushLevel</tt>. If the given level is
     * greater than or equal to the <tt>pushLevel</tt> then <tt>push</tt>
     * is called to write all buffered records to the target output
     * <tt>Handler</tt>.
     *
     * @param  record  description of the log event. A null record is
     *                 silently ignored and is not published
     */
    @Override
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        int ix = (start+count)%buffer.length;
        buffer[ix] = record;
        if (count < buffer.length) {
            count++;
        } else {
            start++;
            start %= buffer.length;
        }
        if (record.getLevel().intValue() >= pushLevel.intValue()) {
            push();
        }
    }

    /**
     * Push any buffered output to the target <tt>Handler</tt>.
     * <p>
     * The buffer is then cleared.
     */
    public synchronized void push() {
        for (int i = 0; i < count; i++) {
            int ix = (start+i)%buffer.length;
            LogRecord record = buffer[ix];
            target.publish(record);
        }
        // Empty the buffer.
        start = 0;
        count = 0;
    }

    /**
     * Causes a flush on the target <tt>Handler</tt>.
     * <p>
     * Note that the current contents of the <tt>MemoryHandler</tt>
     * buffer are <b>not</b> written out.  That requires a "push".
     */
    @Override
    public void flush() {
        target.flush();
    }

    /**
     * Close the <tt>Handler</tt> and free all associated resources.
     * This will also close the target <tt>Handler</tt>.
     *
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    @Override
    public void close() throws SecurityException {
        target.close();
        setLevel(Level.OFF);
    }

    /**
     * Set the <tt>pushLevel</tt>.  After a <tt>LogRecord</tt> is copied
     * into our internal buffer, if its level is greater than or equal to
     * the <tt>pushLevel</tt>, then <tt>push</tt> will be called.
     *
     * @param newLevel the new value of the <tt>pushLevel</tt>
     * @exception  SecurityException  if a security manager exists and if
     *             the caller does not have <tt>LoggingPermission("control")</tt>.
     */
    public synchronized void setPushLevel(Level newLevel) throws SecurityException {
        if (newLevel == null) {
            throw new NullPointerException();
        }
        checkPermission();
        pushLevel = newLevel;
    }

    /**
     * Get the <tt>pushLevel</tt>.
     *
     * @return the value of the <tt>pushLevel</tt>
     */
    public Level getPushLevel() {
        return pushLevel;
    }

    /**
     * Check if this <tt>Handler</tt> would actually log a given
     * <tt>LogRecord</tt> into its internal buffer.
     * <p>
     * This method checks if the <tt>LogRecord</tt> has an appropriate level and
     * whether it satisfies any <tt>Filter</tt>.  However it does <b>not</b>
     * check whether the <tt>LogRecord</tt> would result in a "push" of the
     * buffer contents. It will return false if the <tt>LogRecord</tt> is null.
     * <p>
     * @param record  a <tt>LogRecord</tt>
     * @return true if the <tt>LogRecord</tt> would be logged.
     *
     */
    @Override
    public boolean isLoggable(LogRecord record) {
        return super.isLoggable(record);
    }
}