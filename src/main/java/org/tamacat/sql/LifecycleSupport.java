/*
 * Copyright (c) 2007, tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

public interface LifecycleSupport {

    /**
     * Start this component.
     * Should not throw an exception if the component is already running.
     * <p>In the case of a container, this will propagate the start signal
     * to all components that apply.
     */
    void start();

    /**
     * Stop this component.
     * Should not throw an exception if the component isn't started yet.
     * <p>In the case of a container, this will propagate the stop signal
     * to all components that apply.
     */
    void stop();

    /**
     * Check whether this component is currently running.
     * <p>In the case of a container, this will return <code>true</code>
     * only if <i>all</i> components that apply are currently running.
     * @return whether the component is currently running
     */
    boolean isRunning();
}
