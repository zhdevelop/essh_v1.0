package com.eryansky.common.orm.jpa;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.eryansky.common.orm.Page;
import com.eryansky.common.utils.reflection.ReflectionUtils;

/**
 * 对jpa2.0再封装,只要对JPQL查询进行扩展.支持j2EE6.
 * 
 * @author 尔演&Eryan eryanwcp@gmail.com
 * @date 2013-3-30 下午10:35:23
 * 
 * @param <T>
 * @param <ID>
 */
@SuppressWarnings("unchecked")
public class JpaDao<T, ID extends Serializable> {

	protected Logger logger = LoggerFactory.getLogger(getClass());

	public JpaDao() {
		this.entityClass = ReflectionUtils.getClassGenricType(getClass());
	}

	public JpaDao(Class<T> entityClass) {
		this.entityClass = entityClass;
	}

	@PersistenceContext
	EntityManager entityManager;

	protected Class<T> entityClass;

	@Autowired
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * 按JPQL查询对象列表.
	 * 
	 * @param values
	 *            数量可变的参数,按顺序绑定.
	 */
	public <X> List<X> find(final String jpql, final Object... values) {
		logger.debug("find jpql: {} # param: {}", jpql, values);
		return createQuery(jpql, values).getResultList();
	}

	/**
	 * 按JPQL查询对象列表.
	 * 
	 * @param values
	 *            命名参数,按名称绑定.
	 */
	public <X> List<X> find(final String jpql, final Map<String, ?> values) {
		logger.debug("find jpql: {} # param: {}", jpql, values);
		return createQuery(jpql, values).getResultList();
	}

	/**
	 * 按JPQL查询唯一对象.
	 * 
	 * @param values
	 *            数量可变的参数,按顺序绑定.
	 */
	public <X> X findUnique(final String jpql, final Object... values) {
		logger.debug("findUnique jpql: {} # param: {}", jpql, values);
		return (X) createQuery(jpql, values).getSingleResult();
	}

	public Long count(final String jpql, final Object... values) {

		return (Long) createQuery(jpql, values).getSingleResult();
	}

	/**
	 * 按JPQL查询唯一对象.
	 * 
	 * @param values
	 *            命名参数,按名称绑定.
	 */
	public <X> X findUnique(final String jpql, final Map<String, ?> values) {
		logger.debug("findUnique jpql: {} # param: {}", jpql, values);
		return (X) createQuery(jpql, values).getSingleResult();
	}

	/**
	 * 执行JPQL进行批量修改/删除操作.
	 * 
	 * @param values
	 *            数量可变的参数,按顺序绑定.
	 * @return 更新记录数.
	 */
	public int batchExecute(final String jpql, final Object... values) {
		logger.debug("batchExecute jpql: {} # param: {}", jpql, values);
		return createQuery(jpql, values).executeUpdate();
	}

	/**
	 * 执行JPQL进行批量修改/删除操作.
	 * 
	 * @param values
	 *            命名参数,按名称绑定.
	 * @return 更新记录数.
	 */
	public int batchExecute(final String jpql, final Map<String, ?> values) {
		logger.debug("batchExecute jpql: {} # param: {}", jpql, values);
		return createQuery(jpql, values).executeUpdate();
	}

	/**
	 * 按JPQL查询对象列表.
	 * 
	 * @param values
	 *            数量可变的参数,按顺序绑定.
	 */
	public Query createQuery(final String queryString, final Object... values) {
		Validate.notBlank(queryString, "queryString不能为空");
		Query query = getEntityManager().createQuery(queryString);
		if (values != null) {
			for (int i = 0; i < values.length; i++) {
				query.setParameter(i + 1, values[i]);
			}
		}
		return query;
	}

	/**
	 * 根据查询JPQL与参数列表创建Query对象. 与find()函数可进行更加灵活的操作.
	 * 
	 * @param values
	 *            命名参数,按名称绑定.
	 */
	@SuppressWarnings("rawtypes")
	public Query createQuery(final String queryString,
			final Map<String, ?> values) {
		Validate.notBlank(queryString, "queryString不能为空");
		Query query = getEntityManager().createQuery(queryString);
		if (values != null) {
			String key;
			String value;
			for (Iterator it = values.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();
				key = entry.getKey().toString();
				value = entry.getValue().toString();
				query.setParameter(key, value);
			}
		}
		return query;
	}

	/**
	 * 取得对象的主键名.
	 */
	public String getIdName() {
		return String.valueOf("id");
	}

	/**
	 * Flush当前Session.
	 */
	public void flush() {
		entityManager.flush();
	}

	/**
	 * ================= 分页操作封装 =================
	 */
	/**
	 * 按JPQL分页查询.
	 * 
	 * @param pageRequest
	 *            分页参数.
	 * @param jpql
	 *            jpql语句.
	 * @param values
	 *            数量可变的查询参数,按顺序绑定.
	 * 
	 * @return 分页查询结果, 附带结果列表及所有查询输入参数.
	 */
	public Page<T> findPage(final Page<T> page, String jpql,
			final Object... values) {
		Validate.notNull(page, "page不能为空");
		long totalCount = countJpqlResult(jpql, values);
		jpql = setOrderParameterToJpql(jpql, page);
		Query q = createQuery(jpql, values);
		setPageParameterToQuery(q, page);
		page.setResult(q.getResultList());
		page.setTotalCount(totalCount);
		return page;
	}

	/**
	 * 在JPQL的后面添加分页参数定义的orderBy, 辅助函数.
	 */
	protected String setOrderParameterToJpql(final String jpql, final Page<T> page) {
		if (page.getOrderBy() == null)
			return jpql;
		StringBuilder builder = new StringBuilder(jpql);
		builder.append(" order by ");

		if (page.isOrderBySetted()) {
			String[] orderByArray = StringUtils.split(page.getOrderBy(), ',');
			String[] orderArray = StringUtils.split(page.getOrder(), ',');

			Validate.isTrue(orderByArray.length == orderArray.length,
					"分页多重排序参数中,排序字段与排序方向的个数不相等");

			for (int i = 0; i < orderByArray.length; i++) {
				if (Page.ASC.equals(orderArray[i])) {
					builder.append(" ").append(orderByArray[i]).append(" ")
							.append(Page.ASC).append(",");
				} else {
					builder.append(" ").append(orderByArray[i]).append(" ")
							.append(Page.DESC).append(",");
				}
			}
		}
		builder.deleteCharAt(builder.length() - 1);
		return builder.toString();
	}

	/**
	 * 设置分页参数到Query对象,辅助函数.
	 */
	protected Query setPageParameterToQuery(final Query q, final Page<T> page) {
		q.setFirstResult(page.getFirst() - 1);
		q.setMaxResults(page.getPageSize());
		return q;
	}

	/**
	 * 执行count查询获得本次Jpql查询所能获得的对象总数.
	 * 
	 * 本函数只能自动处理简单的jpql语句,复杂的jpql查询请另行编写count语句查询.
	 */
	protected Long countJpqlResult(final String jpql, final Object... values) {
		String countJpql = prepareCountJpql(jpql);
		try {
			Long count = count(countJpql, values);
			return count;
		} catch (Exception e) {
			throw new RuntimeException("jpql can't be auto count, jpql is:"
					+ countJpql, e);
		}
	}

	/**
	 * 执行count查询获得本次Jpql查询所能获得的对象总数.
	 * 
	 * 本函数只能自动处理简单的jpql语句,复杂的jpql查询请另行编写count语句查询.
	 */
	protected long countJpqlResult(final String jpql, final Map<String, ?> values) {
		String countJpql = prepareCountJpql(jpql);

		try {
			Long count = findUnique(countJpql, values);
			return count;
		} catch (Exception e) {
			throw new RuntimeException("jpql can't be auto count, jpql is:"
					+ countJpql, e);
		}
	}

	private String prepareCountJpql(String orgJpql) {
		String countJpql = "select count (*) "
				+ removeSelect(removeOrders(orgJpql));
		return countJpql;
	}

	private static String removeSelect(String jpql) {
		int beginPos = jpql.toLowerCase().indexOf("from");
		return jpql.substring(beginPos);
	}

	private static String removeOrders(String jpql) {
		Pattern p = Pattern.compile("order\\s*by[\\w|\\W|\\s|\\S]*",
				Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(jpql);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, "");
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
