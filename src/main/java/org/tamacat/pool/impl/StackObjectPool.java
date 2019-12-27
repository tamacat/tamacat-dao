/*
 * Copyright (c) 2008 tamacat.org
 * All rights reserved.
 */
package org.tamacat.pool.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import org.tamacat.log.Log;
import org.tamacat.log.LogFactory;
import org.tamacat.pool.ObjectActivateException;
import org.tamacat.pool.ObjectPool;
import org.tamacat.pool.PoolLimitException;
import org.tamacat.pool.PoolableObjectFactory;

public class StackObjectPool<T> implements ObjectPool<T> {

    static final Log LOG = LogFactory.getLog(StackObjectPool.class);
	
    private final PoolableObjectFactory<T> factory;

    private final Stack<T> pool = new Stack<>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger max = new AtomicInteger();

    public StackObjectPool(PoolableObjectFactory<T> factory) {
        this.factory = factory;
        
        //Set maximum number of pooling size.
        setMaxPoolObject(factory.getMaxPools());
        
        //Create init pools
        int init = factory.getInitPools();
        if (init > 0 && max.get() >= init) {
            LOG.debug("Creating initial pools: " + factory.getInitPools());
            List<T> list = new ArrayList<>();
            for (int i=0; i<init; i++) {
                list.add(getObject());
            }
            for (T o : list) {
                free(o);
            }
        }
    }

    @Override
    public synchronized void free(T object) {
        try {
            if (factory.validate(object)) {
                pool.push(object);
            }
        } finally {
            if (active.get() > 0) {
                active.decrementAndGet();
            }
        }
        if (LOG.isDebugEnabled()) {
        	LOG.debug("free() active="+active.get()+", pool="+pool.size()+", max="+max.get());
        }
    }

    @Override
    public synchronized T getObject() {
        T object = null;
        if (pool.size() > 0) {
            object = pool.pop();
            try {
                LOG.trace("activate");
                factory.activate(object);
                active.incrementAndGet();
            } catch (ObjectActivateException e) {
                //Activate error -> destroy pooled object and retry get object.
                LOG.warn("retry. " + e.getMessage());
                try {
                    factory.destroy(object);
                } finally {
                    if (active.get() > 0) {
                        active.decrementAndGet();
                    }
                }
                object = getObject();
            } 
        } else {
            object = create();
            if (object != null) {
                active.incrementAndGet();
            }
        }
    	return object;
    }

    protected synchronized T create() {
        if (max.get() <= 0 || active.get() < max.get()) {
            return factory.create();
        } else {
            //Limited MaxPools
            throw new PoolLimitException(
                "Maximum number of pools: active="+active.get()+", pool="+pool.size()+", max="+max.get()
            );
        }
    }

    @Override
    public void setMaxPoolObject(int max) {
        this.max.set(max);
    }

    @Override
    public synchronized void release() {
        try {
            for (T o : pool) {
                factory.destroy(o);
            }
        } finally {
            pool.clear();
            active.set(0);
        }
    }

    @Override
    public int getNumberOfMaxPoolObjects() {
        return max.get();
    }

    @Override
    public int getNumberOfPooledObjects() {
        return pool.size();
    }

    @Override
    public int getNumberOfActiveObjects() {
        return active.get();
    }
}
