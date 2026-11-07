package com.catolica.ideiaspace.security.rls;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class RlsDataSource extends DelegatingDataSource {

    public RlsDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection target = super.getConnection();
        return RlsConnectionInvocationHandler.wrap(target);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection target = super.getConnection(username, password);
        return RlsConnectionInvocationHandler.wrap(target);
    }
}
