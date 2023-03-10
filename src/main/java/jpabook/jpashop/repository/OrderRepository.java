package jpabook.jpashop.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jpabook.jpashop.domain.*;
import jpabook.jpashop.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

import static jpabook.jpashop.domain.QMember.member;
import static jpabook.jpashop.domain.QOrder.order;

@Repository
public class OrderRepository {

    private final EntityManager em;
    private final JPAQueryFactory query;

    public OrderRepository(EntityManager em) {
        this.em = em;
        this.query = new JPAQueryFactory(em);
    }

    public void save(Order order) {
        em.persist(order);
    }

    public Order findOne(Long id) {
        return em.find(Order.class, id);
    }

    public List<Order> findAllByString (OrderSearch orderSearch) {

        String jqpl = "select o from Order o join o.member m";
        boolean isFirstCondition = true;

        // 주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            if (isFirstCondition) {
                jqpl += " where";
                isFirstCondition = false;
            } else {
                jqpl += " and";
            }
            jqpl += " o.status = :status";
        }

        if (StringUtils.hasText(orderSearch.getMemberName())) {
            if (isFirstCondition) {
                jqpl += " where";
                isFirstCondition = false;
            } else {
                jqpl += " and";
            }
            jqpl += " m.name like :name";
        }
        TypedQuery<Order> query = em.createQuery(jqpl, Order.class)
                .setMaxResults(1000); // 최대 1000건
        if (orderSearch.getOrderStatus() != null) {
            query = query.setParameter("status", orderSearch.getOrderStatus());
        }

        if (StringUtils.hasText(orderSearch.getMemberName())) {
            query = query.setParameter("name", orderSearch.getMemberName());
        }
        System.out.println("jqpl = " + jqpl);
        return query.getResultList();

//        return em.createQuery("select o from Order o join o.member m" +
//                        " where o.status = :status" +
//                        " and m.name like :name", Order.class)
//                .setParameter("status", orderSearch.getOrderStatus())
//                .setParameter("name", orderSearch.getMemberName())
//                .setMaxResults(1000) // 최대 1000건
//                .getResultList();
    }
    public List<Order> findAllByCriteria(OrderSearch orderSearch) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Order> cq = cb.createQuery(Order.class);
        Root<Order> o = cq.from(Order.class);
        Join<Order, Member> m = o.join("member", JoinType.INNER); //회원과 조인
        List<Predicate> criteria = new ArrayList<>();
        //주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            Predicate status = cb.equal(o.get("status"),
                    orderSearch.getOrderStatus());
            criteria.add(status);
        }
        //회원 이름 검색
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            Predicate name =
                    cb.like(m.<String>get("name"), "%" +
                            orderSearch.getMemberName() + "%");
            criteria.add(name);
        }
        cq.where(cb.and(criteria.toArray(new Predicate[criteria.size()])));
        TypedQuery<Order> query = em.createQuery(cq).setMaxResults(1000); //최대 1000건
        return query.getResultList();
    }

    public List<Order> findAllWithMemberDelivery() {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member" +
                        " join fetch o.delivery", Order.class)
                        .getResultList();
    }


    /**
     * 오더는 2개, 오더아이템은 4개 데이터가 뻥튀기 된다.
     * -> distinct 추가하면 뻥튀기 방지
     * 근데 dateBase는 정말 똑같아야 중복제거가 된다
     * JPA1 Book, JPA2 Book 2개 있기 때문에 DB에서는 중복 제거를 할 수 없다.
     *
     * 근데 JPA에서는 distinct를 사용하면 Order를 갖고 올 때 Order가 같은 아이디면 중복을 제거해주기 때문에 뻥튀기 막을 수 있다.
     * "컬렉션 페치 조인 이거의 단점" => 페이징이 불가능하다!!!!
     * 1 : N을 페치 조인 하는 순간 페이징 불가능!!!! (DB 내에서는 뻥튀기가 되기 때문에 불가능 한 것임)
     * 쿼리를 살펴보면 limit 자체가 없다.
     * 2023-03-09 20:22:36.336  WARN 21044 --- [nio-8080-exec-2] o.h.h.internal.ast.QueryTranslatorImpl   : HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
     * 컬렉션 페치 조인은 1개만 사용할 수 있다. 둘 이상에 페치 조인 사용하면 안 된다!! => 데이터 부정확 위험있음.
     */
    public List<Order> findAllWithItem() {
        return em.createQuery(
                    "select distinct o from Order o" +
                            " join fetch o.member m" +
                            " join fetch o.delivery d" +
                            " join fetch o.orderItems oi" +
                            " join fetch oi.item", Order.class)
                .setFirstResult(0)
                .setMaxResults(100)
                .getResultList();
    }

    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        return em.createQuery(
                        "select o from Order o" +
                                " join fetch o.member" +
                                " join fetch o.delivery", Order.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }


    public List<Order> findAll(OrderSearch orderSearch) {

        return query
                .select(order)
                .from(order)
                .join(order.member, member)
                .where(statusEq(orderSearch.getOrderStatus()), nameLike(orderSearch.getMemberName()))
                .limit(1000)
                .fetch();
    }

    private BooleanExpression nameLike(String memberName) {
        if (!StringUtils.hasText(memberName)) {
            return null;
        }
        return member.name.like(memberName);
    }

    private BooleanExpression statusEq(OrderStatus statusCond) {
        if (statusCond == null) {
            return null;
        }
        return order.status.eq(statusCond);
    }
}
