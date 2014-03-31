package at.bestsolution.persistence.mybatis.impl;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.FastResultSetHandler;
import org.apache.ibatis.executor.resultset.NestedResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

import at.bestsolution.persistence.model.LazyEObject;
import at.bestsolution.persistence.model.ResolveDelegate;
import at.bestsolution.persistence.mybatis.EnvironmentProvider;
import at.bestsolution.persistence.mybatis.MappingProvider;
import at.bestsolution.persistence.mybatis.MappingProvider.MappingUnit;
import at.bestsolution.persistence.mybatis.SqlMetaDataProvider;
import at.bestsolution.persistence.mybatis.SqlMetaDataProvider.Table;
import at.bestsolution.persistence.mybatis.SqlSessionProvider;
import at.bestsolution.persistence.mybatis.impl.CGLibObjectProxyInterceptor.CGLibProxyResolve;

public class SqlSessionProviderImpl implements SqlSessionProvider {
	static ThreadLocal<Boolean> IN_PROXY_RESOLVE = new ThreadLocal<Boolean>();

	private List<MappingProvider> mappingProviders = new ArrayList<MappingProvider>();
	private List<SqlMetaDataProvider> metaDataProviders = new ArrayList<SqlMetaDataProvider>();
	//TODO We need to support multi configurations
	private SqlSessionFactory sessionFactory;
	private Map<Class<?>, EClass> eClassCache = new HashMap<Class<?>, EClass>();
	private Map<Executor, Map<String, EObject>> sessionObjectCache = new HashMap<Executor, Map<String,EObject>>();
	private Map<EObject, EnhancedEObject> proxyCache = new HashMap<EObject, EnhancedEObject>();

	private EnvironmentProvider environment;
	private Map<String, Table> tableMap = new HashMap<String, SqlMetaDataProvider.Table>();

	public SqlSession createSession() {
		return bootstrap().openSession();
	}

	public void setEnvironmentProvider(EnvironmentProvider environment) {
		this.environment = environment;
	}

	public void unsetEnvironmentProvider(EnvironmentProvider environment) {
		this.environment = null;
	}

	public void addMappingProvider(MappingProvider provider) {
		this.mappingProviders.add(provider);
	}

	public void removeMappingProvider(MappingProvider provider) {
		this.mappingProviders.remove(provider);
	}

	public void addSqlMetaDataProvider(SqlMetaDataProvider provider) {
		this.metaDataProviders.add(provider);
		for( Table t : provider.getTables() ) {
			System.err.println("Mapping: " + t.getName());
			tableMap.put(t.getName().toUpperCase(), t);
		}
	}

	public void removeSqlMetaDataProvider(SqlMetaDataProvider provider) {
		this.metaDataProviders.remove(provider);
	}

	private synchronized SqlSessionFactory bootstrap() {
		if( sessionFactory != null ) {
			return sessionFactory;
		}
		TransactionFactory transactionFactory = new JdbcTransactionFactory() {
			@Override
			public Transaction newTransaction(Connection conn) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Transaction newTransaction(DataSource ds,
					TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit) {
				return new ConditionalTransaction(ds, desiredLevel, new AutoCloseJDBCTransaction(ds,desiredLevel,desiredAutoCommit));
//				return new JdbcTransaction(ds, desiredLevel, desiredAutoCommit) {
//					private int i = 0;
//					@Override
//					protected void openConnection() throws SQLException {
//						super.openConnection();
//					}
//
//					@Override
//					public Connection getConnection() throws SQLException {
//						i++;
//						return super.getConnection();
//					}
//
//					@Override
//					public void close() throws SQLException {
//						i--;
//						if( i == 0 ) {
//							super.close();
//							connection = null;
//						}
//					}
//				};
			}
		};
		//TODO We need to support multi configurations
		final PooledDataSource dataSource = new PooledDataSource(environment.getDriverClass(), environment.getJDBCUrl(), environment.getUsername(), environment.getPassword());
		Environment env = new Environment(environment.getEnvironmentId(), transactionFactory, dataSource);
		Configuration cfg = new Configuration(env) {
			@Override
			public Executor newExecutor(Transaction arg0, ExecutorType arg1,
					boolean arg2) {
				if( arg1 == ExecutorType.SIMPLE ) {
					return new SimpleExecutor(this, arg0) {
						@Override
						protected void closeStatement(Statement arg0) {
							super.closeStatement(arg0);
							try {
								transaction.close();
							} catch (SQLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

						@Override
						public void close(boolean arg0) {
							Map<String, EObject> remove = sessionObjectCache.remove(this);
							proxyCache.keySet().removeAll(remove.values());
							super.close(arg0);
						}
					};
				}
				return super.newExecutor(arg0, arg1, arg2);
			}

			@Override
			public ResultSetHandler newResultSetHandler(Executor executor,
					MappedStatement mappedStatement, RowBounds rowBounds,
					ParameterHandler parameterHandler,
					ResultHandler resultHandler, BoundSql boundSql) {
//				return super.newResultSetHandler(executor, mappedStatement, rowBounds,
//						parameterHandler, resultHandler, boundSql);
				Map<String, EObject> objectCache = sessionObjectCache.get(executor);
				if( objectCache == null ) {
					objectCache = new HashMap<String,EObject>();
					sessionObjectCache.put(executor, objectCache);
				}
				final Map<String, EObject> fobjectCache = objectCache;
				if( mappedStatement.hasNestedResultMaps() ) {
					return new NestedResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql,
					        rowBounds) {
						@Override
						protected Object createResultObject(ResultSet rs,
								ResultMap resultMap,
								List<Class<?>> constructorArgTypes,
								List<Object> constructorArgs, String columnPrefix,
								ResultColumnCache resultColumnCache)
								throws SQLException {
							final Class<?> resultType = resultMap.getType();
							String key = resultType.getName() + "#" + rs.getObject(resultMap.getIdResultMappings().get(0).getColumn());
//							System.err.println(key);
							Object rv = fobjectCache.get(key);
							if( rv == null ) {
								rv = super.createResultObject(rs, resultMap, constructorArgTypes,
										constructorArgs, columnPrefix, resultColumnCache);
//								System.err.println("CREATING: " + rv.getClass() + " => " + rv.hashCode());
								if( rv instanceof EObject ) {
									fobjectCache.put(key, (EObject) rv);
//									System.err.println("CACHING IT");
								}
							} else {
//								System.err.println("I AM CACHED: " + rv.hashCode());
							}

							return rv;
						}

						@Override
						protected Object createParameterizedResultObject(
								ResultSet arg0, Class<?> arg1,
								List<ResultMapping> arg2, List<Class<?>> arg3,
								List<Object> arg4, String arg5,
								ResultColumnCache arg6) throws SQLException {
							// TODO Auto-generated method stub
							return super.createParameterizedResultObject(arg0, arg1, arg2, arg3, arg4,
									arg5, arg6);
						}

						@Override
						protected Object instantiateParameterObject(
								Class<?> parameterType) {
							// TODO Auto-generated method stub
							return super.instantiateParameterObject(parameterType);
						}
					};
				} else {
				return new FastResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds) {
					@Override
					protected Object createResultObject(ResultSet rs,
							ResultMap resultMap,
							List<Class<?>> constructorArgTypes,
							List<Object> constructorArgs, String columnPrefix,
							ResultColumnCache resultColumnCache)
							throws SQLException {
						final Class<?> resultType = resultMap.getType();
						String key = resultType.getName() + "#" + rs.getObject(resultMap.getIdResultMappings().get(0).getColumn());
//						System.err.println(key);
						Object rv = fobjectCache.get(key);
						if( rv == null ) {
							rv = super.createResultObject(rs, resultMap, constructorArgTypes,
									constructorArgs, columnPrefix, resultColumnCache);
//							System.err.println("CREATING: " + rv.getClass() + " => " + rv.hashCode());
							if( rv instanceof EObject ) {
								fobjectCache.put(key, (EObject) rv);
//								System.err.println("CACHING IT");
							}
						} else {
//							System.err.println("I AM CACHED: " + rv.hashCode());
						}

						return rv;
					}

					@Override
					protected Object createParameterizedResultObject(
							ResultSet arg0, Class<?> arg1,
							List<ResultMapping> arg2, List<Class<?>> arg3,
							List<Object> arg4, String arg5,
							ResultColumnCache arg6) throws SQLException {
						System.err.println("createParameterizedResultObject");
						// TODO Auto-generated method stub
						return super.createParameterizedResultObject(arg0, arg1, arg2, arg3, arg4,
								arg5, arg6);
					}

					@Override
					protected Object instantiateParameterObject(
							Class<?> parameterType) {
						System.err.println("instantiateParameterObject");
						// TODO Auto-generated method stub
						return super.instantiateParameterObject(parameterType);
					}
				};
				}
			}
		};
		cfg.setDatabaseId(environment.getDatabaseType());
		final ObjectFactory objFactory = cfg.getObjectFactory();
		cfg.setObjectFactory(new ObjectFactory() {

			public void setProperties(Properties arg0) {
			}

			@SuppressWarnings({ "rawtypes", "unchecked" })
			public boolean isCollection(Class arg0) {
				return objFactory.isCollection(arg0);
			}

			@SuppressWarnings({ "rawtypes", "unchecked" })
			public Object create(Class arg0, List arg1, List arg2) {
				return null;
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			public Object create(Class arg0) {
				EClass e = eClassCache.get(arg0);
				if( e != null ) {
					return EcoreUtil.create(e);
				}

				return objFactory.create(arg0);
			}
		});
		cfg.setCacheEnabled(true);
		final ObjectWrapperFactory orig = cfg.getObjectWrapperFactory();
		cfg.setLazyLoadingEnabled(true);
		cfg.setAggressiveLazyLoading(false);
		cfg.setLazyLoadTriggerMethods(new HashSet<String>());
		cfg.setProxyFactory(new EObjectProxy(new CglibProxyFactory()));
		cfg.setObjectWrapperFactory(new ObjectWrapperFactory() {
			@Override
			public boolean hasWrapperFor(Object arg0) {
				if( arg0 instanceof EObject ) {
					return true;
				}
//				System.err.println("hasWrapperFor: " +arg0);
				// TODO Auto-generated method stub
				return orig.hasWrapperFor(arg0);
			}

			@Override
			public ObjectWrapper getWrapperFor(MetaObject arg0, Object arg1) {
//				System.err.println("getWrapperFor: " +arg0 + " => " + arg1);
				// TODO Auto-generated method stub
				if( arg1 instanceof EObject ) {
					return new EMFObjectWrapper(arg0, (EObject) arg1);
				}
				return orig.getWrapperFor(arg0, arg1);
			}
		});
		cfg.getTypeHandlerRegistry().register(Blob.class, new BaseTypeHandler<Blob>() {
			@Override
			  public void setNonNullParameter(PreparedStatement ps, int i, Blob parameter, JdbcType jdbcType)
			      throws SQLException {
				ps.setBlob(i, parameter.getBinaryStream());
			  }

			  @Override
			  public Blob getNullableResult(ResultSet rs, String columnName)
			      throws SQLException {
				  if( rs.getBlob(columnName) == null ) {
					 return null;
				  }

				  ResultSetMetaData data = rs.getMetaData();
				  int idx = rs.findColumn(columnName);
				  String tableName = data.getTableName(idx);
				  Table t = tableMap.get(tableName.toUpperCase());

				  String primaryKeyColumn = t.getPrimaryKeyColumn().getName();
				  String localPrimaryKeyColumn = primaryKeyColumn;

				  // Looks like we deal with alias stuff
				  if( t.getColumn(columnName) == null ) {
					  localPrimaryKeyColumn = columnName.substring(0,columnName.indexOf('_')) + "_" + localPrimaryKeyColumn;
					  columnName = columnName.substring(columnName.indexOf('_')+1);
				  }

				  return new LazyBlob(dataSource, tableName, columnName, primaryKeyColumn, rs.getObject(localPrimaryKeyColumn));
			  }

			  @Override
			  public Blob getNullableResult(ResultSet rs, int columnIndex)
			      throws SQLException {
				  if( rs.getBlob(columnIndex) == null ) {
					  return null;
				  }

				  ResultSetMetaData data = rs.getMetaData();
				  String columnName = data.getColumnName(columnIndex);

				  return getNullableResult(rs, columnName);
			  }

			  @Override
			  public Blob getNullableResult(CallableStatement cs, int columnIndex)
			      throws SQLException {
				  if( cs.getBlob(columnIndex) == null ) {
					  return null;
				  }
				  ResultSetMetaData data = cs.getMetaData();
				  String tableName = data.getTableName(columnIndex);
				  Table t = tableMap.get(tableName.toUpperCase());
				  return new LazyBlob(dataSource, tableName, data.getColumnName(columnIndex), t.getPrimaryKeyColumn().getName(), cs.getObject(t.getPrimaryKeyColumn().getName()));
			  }

		});
		for( MappingProvider p : mappingProviders ) {
			for( MappingUnit u : p.getMappingUnits() ) {
				cfg.getTypeAliasRegistry().registerAlias(u.getModelInterface().getName(),u.getModelInterface());
				cfg.addMapper(u.getMapperInterface());
				eClassCache.put(u.getModelInterface(), u.getEClass());
			}
		}

		for( MappingProvider p : mappingProviders ) {
			for( MappingUnit u : p.getMappingUnits() ) {
				InputStream in = null;
				try {
					in = u.getMappingXML();
					XMLMapperBuilder b = new XMLMapperBuilder(in, cfg, u.getId(), cfg.getSqlFragments());
					b.parse();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					if( in != null ) {
						try {
							in.close();
						} catch (IOException e) {
						}
					}
				}
			}
		}
		sessionFactory = new SqlSessionFactoryBuilder().build(cfg);

		return sessionFactory;
	}

	class EObjectProxy implements ProxyFactory {
		private ProxyFactory original;

		public EObjectProxy(ProxyFactory original) {
			this.original = original;
		}

		@Override
		public Object createProxy(Object arg0, final ResultLoaderMap arg1,
				Configuration arg2, ObjectFactory arg3, List<Class<?>> arg4,
				List<Object> arg5) {
			if( arg0 instanceof LazyEObject ) {
				//TODO We should optimize this and not create ResolveDelegate instances
				// we could store the object => resultloadermap in a hash and look it up
				LazyEObject leo = (LazyEObject) arg0;
				if( ! leo.isEnhanced() ) {
					leo.setProxyDelegate(new ResolveDelegate() {

						@Override
						public boolean resolve(LazyEObject eo, Object proxyData, EStructuralFeature f) {
							if( IN_PROXY_RESOLVE.get() == Boolean.TRUE ) {
								return false;
							}
							try {
								IN_PROXY_RESOLVE.set(Boolean.TRUE);
								if( arg1.hasLoader(f.getName()) ) {
									try {
										arg1.load(f.getName());
										return true;
									} catch (SQLException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							} finally {
								IN_PROXY_RESOLVE.set(Boolean.FALSE);
							}
							return false;
						}
					});
				}
				return arg0;
			} else {
				if( arg0 instanceof EObject ) {
					EnhancedEObject rv = proxyCache.get(arg0);
					if( rv == null ) {
						final EnhancedEObject fRv = new EnhancedEObject();
						rv = fRv;
//						rv.enhancedObject = (EObject) original.createProxy(arg0, arg1, arg2, arg3, arg4, arg5);
						rv.enhancedObject = CGLibObjectProxyInterceptor.newInstance((EObject) arg0, new CGLibProxyResolve() {

							@Override
							public boolean isResolved(EObject object,
									EStructuralFeature f) {
								return fRv.resolved.containsKey(f);
							}

							@Override
							public void markResolved(EObject object,
									EStructuralFeature f) {
								fRv.resolved.put((EReference) f, Boolean.TRUE);
							}

							@Override
							public boolean resolve(EObject object,
									EStructuralFeature f) {
								if( IN_PROXY_RESOLVE.get() == Boolean.TRUE ) {
									return false;
								}
								try {
									IN_PROXY_RESOLVE.set(Boolean.TRUE);
									if( arg1.hasLoader(f.getName()) ) {
										try {
											arg1.load(f.getName());
											return true;
										} catch (SQLException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								} finally {
									IN_PROXY_RESOLVE.set(Boolean.FALSE);
								}
								return false;
							}
						});
						proxyCache.put((EObject) arg0, rv);
					}
					return rv.enhancedObject;
				}
				return original.createProxy(arg0, arg1, arg2, arg3, arg4, arg5);
			}
		}

		@Override
		public void setProperties(Properties arg0) {
			// TODO Auto-generated method stub

		}
	}

	static class EnhancedEObject {
		EObject enhancedObject;
		Map<EReference, Boolean> resolved = new HashMap<EReference, Boolean>();
	}

	static class ConditionalTransaction implements Transaction {
		private Transaction originalTransaction;
		private final DataSource ds;
		private final TransactionIsolationLevel desiredLevel;

		public ConditionalTransaction(DataSource ds,
				TransactionIsolationLevel desiredLevel, Transaction originalTransaction) {
			this.originalTransaction = originalTransaction;
			this.ds = ds;
			this.desiredLevel = desiredLevel;
		}

		private boolean inTransaction() {
			return SessionFactoryImpl.TRANSACTION_CONNECTION.get() != null;
		}

		private boolean connectionRetrivial() {
			return SessionFactoryImpl.TRANSACTION_CONNECTION_RETRIEVAL.get() == Boolean.TRUE;
		}

		@Override
		public void close() throws SQLException {
			if( ! inTransaction() ) {
				originalTransaction.close();
			}
		}

		@Override
		public void commit() throws SQLException {
			if( ! inTransaction() ) {
				originalTransaction.commit();
			}
		}

		@Override
		public Connection getConnection() throws SQLException {
			if( connectionRetrivial() ) {
				Connection connection = ds.getConnection();
				connection.setAutoCommit(false);
			    if (desiredLevel != null) {
			    	connection.setTransactionIsolation(desiredLevel.getLevel());
			    }
			    return connection;
			}

			if( ! inTransaction() ) {
				return originalTransaction.getConnection();
			}
			return SessionFactoryImpl.TRANSACTION_CONNECTION.get().peek();
		}

		@Override
		public void rollback() throws SQLException {
			if( ! inTransaction() ) {
				originalTransaction.rollback();
			}
		}
	}

	static class AutoCloseJDBCTransaction extends JdbcTransaction {
		private int i = 0;

		public AutoCloseJDBCTransaction(DataSource ds,
				TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit) {
			super(ds, desiredLevel, desiredAutoCommit);
		}

		@Override
		protected void openConnection() throws SQLException {
			super.openConnection();
		}

		@Override
		public Connection getConnection() throws SQLException {
			i++;
			return super.getConnection();
		}

		@Override
		public void close() throws SQLException {
			i--;
			if( i == 0 ) {
				super.close();
				connection = null;
			}
		}
	}
}