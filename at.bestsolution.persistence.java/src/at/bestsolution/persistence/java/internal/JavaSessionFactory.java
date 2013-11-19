package at.bestsolution.persistence.java.internal;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

import at.bestsolution.persistence.ObjectMapper;
import at.bestsolution.persistence.Session;
import at.bestsolution.persistence.SessionFactory;
import at.bestsolution.persistence.java.DatabaseSupport;
import at.bestsolution.persistence.java.JDBCConnectionProvider;
import at.bestsolution.persistence.java.JavaSession;
import at.bestsolution.persistence.java.ObjectMapperFactoriesProvider;
import at.bestsolution.persistence.java.ObjectMapperFactory;
import at.bestsolution.persistence.java.ProxyFactory;
import at.bestsolution.persistence.java.SessionCache;
import at.bestsolution.persistence.java.SessionCacheFactory;

public class JavaSessionFactory implements SessionFactory {
	JDBCConnectionProvider connectionProvider;
	ProxyFactory proxyFactory;
	SessionCacheFactory cacheFactory;
	Map<Class<? extends ObjectMapper<?>>, ObjectMapperFactory<?>> factories = new HashMap<Class<? extends ObjectMapper<?>>, ObjectMapperFactory<?>>();
	Map<String,DatabaseSupport> databaseSupports = new HashMap<>();

	public void registerConfiguration(JDBCConnectionProvider connectionProvider) {
		this.connectionProvider = connectionProvider;
	}

	public void unregisterConfiguration(JDBCConnectionProvider connectionProvider) {
		this.connectionProvider = null;
	}

	public void registerMapperFactoriesProvider(ObjectMapperFactoriesProvider provider) {
		factories.putAll(provider.getMapperFactories());
	}

	public void unregisterMapperFactoriesProvider(ObjectMapperFactoriesProvider provider) {
		factories.keySet().removeAll(provider.getMapperFactories().keySet());
	}

	public void registerProxyFactory(ProxyFactory proxyFactory) {
		this.proxyFactory = proxyFactory;
	}

	public void unregisterProxyFactory(ProxyFactory proxyFactory) {
		this.proxyFactory = null;
	}

	public void registerSessionCacheFactory(SessionCacheFactory cacheFactory) {
		this.cacheFactory = cacheFactory;
	}

	public void unregisterSessionCacheFactory(SessionCacheFactory cacheFactory) {
		this.cacheFactory = null;
	}

	public void registerDatabaseSupport(DatabaseSupport databaseSupport) {
		databaseSupports.put(databaseSupport.getDatabaseType(), databaseSupport);
	}

	public void unregisterDatabaseSupport(DatabaseSupport databaseSupport) {
		databaseSupports.remove(databaseSupport.getDatabaseType());
	}

	@Override
	public Session createSession() {
		return new JavaSessionImpl(cacheFactory.createCache());
	}

	class JavaSessionImpl implements JavaSession {
		private String id = UUID.randomUUID().toString();
		private Map<Class<?>, ObjectMapper<?>> mapperInstances = new HashMap<>();
		private Stack<Connection> transactionQueue;
		private SessionCache sessionCache;

		public JavaSessionImpl(SessionCache sessionCache) {
			this.sessionCache = sessionCache;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public DatabaseSupport getDatabaseSupport() {
			return databaseSupports.get(getDatabaseType());
		}

		@Override
		@SuppressWarnings("unchecked")
		public <M extends ObjectMapper<?>> M createMapper(Class<M> mapper) {
			M m = (M) mapperInstances.get(mapper);
			if( m == null ) {
				m = (M) factories.get(mapper).createMapper(this);
			}
			return m;
		}

		@Override
		public void runInTransaction(Transaction transaction) {
			Connection connection = connectionProvider.checkoutConnection();
			if( transactionQueue == null ) {
				transactionQueue = new Stack<>();
			}
			transactionQueue.add(connection);
			try {
				if( transaction.execute() ) {
					try {
						connection.commit();
					} catch( SQLException e ) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					try {
						connection.rollback();
					} catch( SQLException e ) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} catch (Throwable e) {
				try {
					connection.rollback();
				} catch (SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				throw e;
			} finally {
				connectionProvider.returnConnection(transactionQueue.pop());
			}
		}

		@Override
		public void close() {
			mapperInstances.clear();
			sessionCache.release();
		}

		@Override
		public Connection checkoutConnection() {
			if( transactionQueue != null ) {
				return transactionQueue.peek();
			}
			return connectionProvider.checkoutConnection();
		}

		@Override
		public void returnConnection(Connection connection) {
			if( transactionQueue != null ) {
				return;
			}
			connectionProvider.returnConnection(connection);
		}

		@Override
		public String getDatabaseType() {
			return connectionProvider.getDatabaseType();
		}

		@Override
		public SessionCache getCache() {
			return sessionCache;
		}

		@Override
		public ProxyFactory getProxyFactory() {
			return proxyFactory;
		}

		@Override
		public Object convertType(Class<?> targetType, Object value) {
			if( targetType == Boolean.class ) {
				if( value instanceof Number ) {
					return ((Number)value).intValue() != 0;
				}
			}
			return value;
		}

		@Override
		public Blob handleBlob(String tableName, String blobColumnName,
				String idColumnName, ResultSet set) throws SQLException {
			return new LazyBlob(this, tableName, blobColumnName, idColumnName, set.getObject(idColumnName));
		}
	}
}