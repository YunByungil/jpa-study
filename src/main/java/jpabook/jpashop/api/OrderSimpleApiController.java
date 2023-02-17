package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * xToOne 관계라는 것을 잊지 말자. ToOne 관계를 어떻게 해결할 것인지?
 * ManyToOne, OneToOne 성능 최적화 하는 방법!-!
 * Order
 * Order -> Member
 * Order -> Delivery (이 두 개만 필요한 Api)
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    /*
    엔티티 직접 노출 -> 프록시 객체 에러 (지연 로딩이기 때문에)
    라이브러리 등록, @Bean Hibernate5Module 이것을 등록해야 에러 잡을 수 있다.
    등록만 하면 => lazy 로딩을 무시해서 갖고옴. (null 값을 들고 옴)
    메서드 추가하면
    강제 지연 로딩으로 강제로 바꿔줌.
     */
    /*
    해결 방법은 Entity를 그냥 애초에 노출 안 하면 되고
    난 Member, Delivery만 사용하려고 하는데 나머지 사용하지 않는 값들을 다 들고 와야 되기 때문에
    성능에도 문제가 생긴다. 강제 지연 로딩을 사용하면 안 된다.
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        // 강제 레이지 로딩 기능을 끄고 내가 원하는 값만 출력하는 방법
        for (Order order : all) {
            order.getMember().getName(); // Lazy 강제 초기화
            order.getDelivery().getAddress(); // Lazy 강제 초기화
        }
        return all;
    }

    /*
    이거에 문제점은 Orders 테이블, Member 테이블, Delivery 테이블 3개를 건드려야 한다.........
    근데 막상 해보니까 쿼리가 5번 나가네 왜일까?
    ORDER -> SQL 1번 실행 -> 결과 주문수가 2개
    처음 돌 때, 첫번째 주문수 Member와 Delivery를 찾잖아 그렇게 쿼리 한 번씩 나가고 (첫번째 SimpleOrderDto 생성 됨)
    주문수가 2개이기 때문에 또 LAZY 초기화 -> Member, Delivery 또 조회한다. => N + 1 문제임!
     */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {
        // ORDER 2개
        /*
         N + 1 -> 회원 N + 배송 N 따라서 쿼리가 5개 실행이 된 거임.
         */
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());

        // 2개
        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(Collectors.toList());

        return result;
    }

    /**
     * 이 모든 것을 다 해결할 수 있는 fetch join 사용해보자 (복습)
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();

        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(Collectors.toList());

        return result;
    }

    /**
     * Entity 조회 말고,
     * Dto 에서 바로 조회하자 -> 필요한 값만 불러오고 싶기 때문이다.
     * v3, v4 누가 더 좋은지 판단하기 어렵다..!
     * v3: 다른 Api에서도 활용할 수 있다.
     * v4: 다른 Api에서 활용하기 힘들다. (필요한 값만 불러오기 때문에 성능에서는 조금 더 좋음)
     * repository는 순수한 Order만 조회하는 역할로 사용하고
     * OrderSimpleQuertDto는 따로 빼자.
     */
    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> ordersV4() {
        return orderSimpleQueryRepository.findOrderDtos();
    }


    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName(); // LAZY 초기화 영속성 컨텍스트가 memberId를 갖고 찾아보는데 없으니까 DB 쿼리 날림
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress(); // LAZY 초기화 영속성 컨텍스트가 memberId를 갖고 찾아보는데 없으니까 DB 쿼리 날림
        }
    }
}
