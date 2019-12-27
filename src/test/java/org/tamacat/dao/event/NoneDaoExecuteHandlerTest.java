package org.tamacat.dao.event;

import org.junit.Before;
import org.junit.Test;
import org.tamacat.dao.event.DaoEvent;
import org.tamacat.dao.impl.NoneDaoExecuteHandler;

public class NoneDaoExecuteHandlerTest {

	NoneDaoExecuteHandler handler;
	
	@Before
	public void setUp() throws Exception {
		handler = new NoneDaoExecuteHandler();
	}

	@Test
	public void testHandleAfterExecuteQuery() {
		handler.handleAfterExecuteQuery(null);
	}

	@Test
	public void testHandleAfterExecuteUpdate() {
		handler.handleAfterExecuteUpdate(null);
		handler.handleAfterExecuteUpdate(new DaoEvent() {
			
			@Override
			public void setResult(int result) {				
			}
			@Override
			public void setQuery(String query) {				
			}
			@Override
			public int getResult() {
				return 0;
			}
			@Override
			public String getQuery() {
				return null;
			}
			@Override
			public Class<?> getCallerDao() {
				return null;
			}
		});
	}

	@Test
	public void testHandleBeforeExecuteQuery() {
		handler.handleBeforeExecuteQuery(null);
	}

	@Test
	public void testHandleBeforeExecuteUpdate() {
		handler.handleBeforeExecuteUpdate(null);
	}
}
