/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.sql;

public class TransactionStateManager {
	
	static final TransactionStateManager SINGLETON = new TransactionStateManager();
	
	private static enum State{
		END(0), BEGIN(10), EXECUTED(20), ROLLBACK(30), COMMIT(40);
		
		private final int state;
		State(int state) {
			this.state = state;
		}
		
		public int getValue() {
			return state;
		}
	}
	
	
	public static TransactionStateManager getInstance() {
		return SINGLETON;
	}
	
    private ThreadLocal<Boolean> isTran = new ThreadLocal<>();
    private ThreadLocal<Integer> state = new ThreadLocal<>();
    
    public TransactionStateManager(){}
    
    public boolean isTransactionStarted() {
    	Integer value = state.get();
    	Boolean t = isTran.get();
    	return t != null && t == true && value != null && value > 0;
    }
    
    public void begin() {
    	state.set(State.BEGIN.getValue());
    	isTran.set(true);
    }
    
    public void end() {
    	state.set(State.END.getValue());
    	isTran.remove();
    	state.remove();
    }
    
    public void executed() {
    	state.set(State.EXECUTED.getValue());
    }
    
    public void abort() {
    	isTran.remove();
    	state.remove();
    }
    
    public void commit() {
    	state.set(State.COMMIT.getValue());
    }
    
    public void rollback() {
    	state.set(State.ROLLBACK.getValue());
    }
    
    public boolean isNotCommited() {
    	int state = this.state.get();
    	return state == State.EXECUTED.getValue();
    }
}
