/*
 * Copyright (c) 2008, tamacat.org
 * All rights reserved.
 */
package org.tamacat.mock.sql;

import java.util.Properties;

import javax.sql.DataSource;
import javax.naming.Context;
import javax.naming.InitialContext;

public class MockDataSourceRegister {

    static MockDataSourceRegister register = new MockDataSourceRegister();

    public static MockDataSourceRegister getInstance() {
        return register;
    }

    public void registDataSource(String name, DataSource ds) {
        try {
            Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.rmi.registry.RegistryContextFactory");
            env.put(Context.PROVIDER_URL, "rmi://localhost");

            //"com.sun.jndi.fscontext.RefFSContextFactory");
            //"com.mysql.jdbc.jdbc2.optiona.MysqlDataSourceFactory");
            Context ctx = new InitialContext(env);
            ctx.bind(name, ds);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
