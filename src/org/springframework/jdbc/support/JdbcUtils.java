/*
 * Copyright 2002-2004 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jdbc.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.SqlLobValue;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;

/**
 * Utility methods for SQL statements.
 * @author Isabelle Muszynski
 * @author Thomas Risberg
 * @author Juergen Hoeller
 * @version $Id: JdbcUtils.java,v 1.11 2004-06-10 17:17:05 trisberg Exp $
 */
public class JdbcUtils {

	private static final Log logger = LogFactory.getLog(JdbcUtils.class);

	/**
	 * Close the given JDBC Statement and ignore any thrown exception.
	 * This is useful for typical finally blocks in manual JDBC code.
	 * @param stmt the JDBC Statement to close
	 */
	public static void closeStatement(Statement stmt) {
		if (stmt != null) {
			try {
				stmt.close();
			}
			catch (SQLException ex) {
				logger.warn("Could not close JDBC Statement", ex);
			}
		}
	}

	/**
	 * Close the given JDBC ResultSet and ignore any thrown exception.
	 * This is useful for typical finally blocks in manual JDBC code.
	 * @param rs the JDBC ResultSet to close
	 */
	public static void closeResultSet(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			}
			catch (SQLException ex) {
				logger.warn("Could not close JDBC ResultSet", ex);
			}
		}
	}

	/**
	 * Extract database meta data.
	 * <p>This method will open a connection to the database and retrieve the database metadata.
	 * Since this method is called before the exception translation feature is configured for
	 * a datasource, this method can not rely on the SQLException translation functionality.
	 * <p>Any exceptions will be wrapped in a MetaDataAccessException. This is a checked exception
	 * and any calling code should catch and handle this exception. You can just log the
	 * error and hope for the best, but there is probably a more serious error that will
	 * reappear when you try to access the database again.
	 * @param dataSource the DataSource to use
	 * @param action callback that will do the actual work
	 * @return object containing the extracted information
	 */
	public static Object extractDatabaseMetaData(DataSource dataSource, DatabaseMetaDataCallback action)
			throws MetaDataAccessException {
		Connection con = null;
		try {
			con = DataSourceUtils.getConnection(dataSource);
			if (con != null) {
				return action.processMetaData(con.getMetaData());
			}
			else {
				throw new MetaDataAccessException("Error while getting connection");
			}
		}
		catch (CannotGetJdbcConnectionException ex) {
			//throw checked exception - we don't want this to be fatal?
			throw new MetaDataAccessException("Error while getting connection",ex);
		}
		catch (DataAccessException ex) {
			//throw checked exception - we don't want this to be fatal?
			throw new MetaDataAccessException("Error while extracting DatabaseMetaData",ex);
		}
		catch (SQLException ex) {
			//throw checked exception - we don't want this to be fatal?
			throw new MetaDataAccessException("Error while extracting DatabaseMetaData",ex);
		}
		finally {
			DataSourceUtils.closeConnectionIfNecessary(con, dataSource);
		}
	}

	/**
	 * Count the occurrences of the character <code>placeholder</code> in an SQL string
	 * <code>str</code>. The character <code>placeholder</code> is not counted if it
	 * appears within a literal as determined by the <code>delim</code> that is passed in.
	 * <p>Examples: If the delimiter is the single quote, and the character to count the
	 * occurrences of is the question mark, then:
	 * <p><code>The big ? 'bad wolf?'</code> gives a count of one.<br>
	 * <code>The big ?? bad wolf</code> gives a count of two.<br>
	 * <code>The big  'ba''ad?' ? wolf</code> gives a count of one.
	 * <p>The grammar of the string passed in should obey the rules of the JDBC spec
	 * which is close to no rules at all: one placeholder per parameter, and it should
	 * be valid SQL for the target database.
	 * @param str string to search in. Returns 0 if this is null
	 * @param placeholder the character to search for and count.
	 * @param delim the delimiter for character literals.
	 */
	public static int countParameterPlaceholders(String str, char placeholder, char delim) {
		int count = 0;
		boolean insideLiteral = false;
		for (int i = 0; str != null && i < str.length(); i++) {
			if (str.charAt(i) == placeholder) {
				if (!insideLiteral)
					count++;
			}
			else {
				if (str.charAt(i) == delim) {
					insideLiteral = insideLiteral ^ true;
				}
			}
		}
		return count;
	}

	/**
	 * Set the value for a parameter.  The method used is based on the SQL Type of the parameter and 
	 * we can handle complex types like Arrays and LOBs.
	 * 
	 * @param ps the prepared statement or callable statement
	 * @param sqlColIndx index of the column we are setting
	 * @param declaredParameter the parameter as it is declared including type
	 * @param inValue the value to set
	 * @throws SQLException
	 */
	public static void setParameterValue(PreparedStatement ps, int sqlColIndx, SqlParameter declaredParameter, Object inValue) throws SQLException {
		LobHandler lh;
		// input parameters must be supplied
		if (inValue == null && declaredParameter.getTypeName() != null) {
			ps.setNull(sqlColIndx, declaredParameter.getSqlType(), declaredParameter.getTypeName());
		}
		else
			if (inValue != null) {
				switch (declaredParameter.getSqlType()) {
					case Types.VARCHAR:
						ps.setString(sqlColIndx, inValue.toString());
						break;
					case Types.BLOB:
						if (inValue instanceof SqlLobValue) {
							lh = ((SqlLobValue) inValue).getLobHandler();
							if (lh == null)
								lh = new DefaultLobHandler();
							LobCreator lc = ((SqlLobValue) inValue).newLobCreator(lh);
							switch (((SqlLobValue) inValue).getType()) {
								case SqlLobValue.STREAM:
									lc.setBlobAsBinaryStream(ps, sqlColIndx,((SqlLobValue) inValue).getStream(), ((SqlLobValue) inValue).getLength());
									break;
								case SqlLobValue.BYTES:
									lc.setBlobAsBytes(ps, sqlColIndx,((SqlLobValue) inValue).getBytes());
									break;
							}
						}
						else {
							ps.setObject(sqlColIndx, inValue, declaredParameter.getSqlType());
						}
						break;
					case Types.CLOB:
						if (inValue instanceof SqlLobValue) {
							lh = ((SqlLobValue) inValue).getLobHandler();
							if (lh == null)
								lh = new DefaultLobHandler();
							LobCreator lc = ((SqlLobValue) inValue).newLobCreator(lh);
							switch (((SqlLobValue) inValue).getType()) {
								case SqlLobValue.STREAM:
									lc.setClobAsAsciiStream(ps, sqlColIndx,((SqlLobValue) inValue).getStream(), ((SqlLobValue) inValue).getLength());
									break;
								case SqlLobValue.READER:
									lc.setClobAsCharacterStream(ps, sqlColIndx,((SqlLobValue) inValue).getReader(), ((SqlLobValue) inValue).getLength());
									break;
								case SqlLobValue.STRING:
									lc.setClobAsString(ps, sqlColIndx,((SqlLobValue) inValue).getString());
									break;
							}
						}
						else {
							ps.setObject(sqlColIndx, inValue, declaredParameter.getSqlType());
						}
						break;
					default:
						ps.setObject(sqlColIndx, inValue, declaredParameter.getSqlType());
						break;
				}
			}
			else {
				ps.setNull(sqlColIndx, declaredParameter.getSqlType());
			}
	}

	/**
	 * Close any LobCreators on any of the parameters passed to an execute method.
	 * Classes using PreparedStatements or CallableStatements should invoke this method 
	 * after every update()/execute() method invokation.
	 * @param parameters parameters supplied. May be null.
	 * @throws InvalidDataAccessApiUsageException if the parameters are invalid
	 */
	public static void cleanupParameters(Object[] parameters) {
		
		if (parameters != null) {
			for (int i = 0; i < parameters.length; i++ ) {
				Object inValue = parameters[i];
				if (inValue instanceof SqlLobValue) {
					((SqlLobValue)inValue).closeLobCreator();
				}
			}
		}

	}

	/**
	 * Check that a SQL type is numeric.
	 * @param sqlType the SQL type to be checked
	 * @return if the type is numeric
	 */
	public static boolean isNumeric(int sqlType) {
		return Types.BIT == sqlType || Types.BIGINT == sqlType || Types.DECIMAL == sqlType ||
				Types.DOUBLE == sqlType || Types.FLOAT == sqlType || Types.INTEGER == sqlType ||
				Types.NUMERIC == sqlType || Types.REAL == sqlType || Types.SMALLINT == sqlType ||
				Types.TINYINT == sqlType;
	}

	/**
	 * Translate a SQL type into one of a few values:
	 * All integer types are translated to Integer.
	 * All real types are translated to Double.
	 * All string types are translated to String.
	 * All other types are left untouched.
	 * @param sqlType the type to be translated into a simpler type
	 * @return the new SQL type
	 */
	public static int translateType(int sqlType) {
		int retType = sqlType;
		if (Types.BIT == sqlType || Types.TINYINT == sqlType || Types.SMALLINT == sqlType ||
				Types.INTEGER == sqlType) {
			retType = Types.INTEGER;
		}
		else if (Types.CHAR == sqlType || Types.VARCHAR == sqlType) {
			retType = Types.VARCHAR;
		}
		else if (Types.DECIMAL == sqlType || Types.DOUBLE == sqlType || Types.FLOAT == sqlType ||
				Types.NUMERIC == sqlType || Types.REAL == sqlType) {
			retType = Types.NUMERIC;
		}
		return retType;
	}

}
