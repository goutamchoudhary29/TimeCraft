package com.timecraft.report.service;

import com.timecraft.admin.entity.SupportTicketStatus;
import com.timecraft.admin.repository.SupportTicketRepository;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.util.Money;
import com.timecraft.coupon.repository.CouponRepository;
import com.timecraft.coupon.service.CouponService.CouponDto;
import com.timecraft.customer.service.CustomerService;
import com.timecraft.customer.service.CustomerService.ProfileDto;
import com.timecraft.inventory.repository.InventoryRepository;
import com.timecraft.inventory.service.InventoryService;
import com.timecraft.inventory.service.InventoryService.InventoryDto;
import com.timecraft.order.dto.OrderDtos.OrderSummaryDto;
import com.timecraft.order.entity.OrderStatus;
import com.timecraft.order.repository.OrderItemRepository;
import com.timecraft.order.repository.OrderRepository;
import com.timecraft.product.entity.ProductStatus;
import com.timecraft.product.repository.ProductRepository;
import com.timecraft.returns.entity.RefundStatus;
import com.timecraft.returns.entity.ReturnRequestStatus;
import com.timecraft.returns.repository.RefundRepository;
import com.timecraft.returns.repository.ReturnRequestRepository;
import com.timecraft.review.entity.ReviewStatus;
import com.timecraft.review.repository.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aggregated business reporting and dashboard analytics service.
 * Uses SQL aggregation queries rather than loading the database into memory.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final CouponRepository couponRepository;
    private final RefundRepository refundRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ProductReviewRepository reviewRepository;
    private final SupportTicketRepository supportTicketRepository;

    public record TopProductStat(Long productId, String productName, String brandName, long unitsSold, BigDecimal revenue) {
    }

    public record TopBrandStat(String brandName, long unitsSold, BigDecimal revenue) {
    }

    public record PeriodBucket(String label, Instant from, Instant to, long orderCount, BigDecimal revenue) {
    }

    public record DashboardOverview(
            long totalCustomers,
            long totalProducts,
            long activeProducts,
            long totalOrders,
            BigDecimal totalRevenue,
            long pendingOrders,
            long completedOrders,
            long cancelledOrders,
            long lowStockCount,
            long pendingReviews,
            long pendingReturns,
            long openTickets,
            BigDecimal todayRevenue,
            BigDecimal weekRevenue,
            BigDecimal monthRevenue,
            BigDecimal yearRevenue,
            long todayOrders,
            long weekOrders,
            long monthOrders,
            long yearOrders,
            List<OrderSummaryDto> recentOrders,
            List<ProfileDto> recentCustomers,
            List<TopProductStat> bestSellingWatches,
            List<TopBrandStat> topBrandsYtd,
            List<InventoryDto> lowStockItems,
            List<PeriodBucket> dailySalesChart,
            List<PeriodBucket> monthlySalesChart,
            List<PeriodBucket> yearlySalesChart
    ) {
    }

    public record SalesReport(
            Instant from,
            Instant to,
            long orderCount,
            BigDecimal totalRevenue,
            BigDecimal averageOrderValue,
            BigDecimal refundedTotal,
            long refundedCount,
            long newCustomerRegistrations,
            List<PeriodBucket> buckets,
            List<TopProductStat> topProducts,
            List<TopBrandStat> topBrands,
            List<CouponDto> coupons
    ) {
    }

    @Transactional(readOnly = true)
    public DashboardOverview getDashboardOverview() {
        Instant now = Instant.now();
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = todayUtc.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfWeek = todayUtc.minusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfMonth = todayUtc.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfYear = todayUtc.withDayOfYear(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long totalCustomers = userRepository.countByRoleName(RoleName.CUSTOMER);
        long totalProducts = productRepository.countByDeletedFalse();
        long activeProducts = productRepository.countByStatusAndDeletedFalse(ProductStatus.ACTIVE);
        long totalOrders = orderRepository.count();
        BigDecimal totalRevenue = Money.of(orderRepository.sumTotalRevenue());

        long pendingOrders = orderRepository.countByStatusIn(List.of(
                OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
                OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY));
        long completedOrders = orderRepository.countByStatusIn(List.of(OrderStatus.DELIVERED));
        long cancelledOrders = orderRepository.countByStatusIn(List.of(OrderStatus.CANCELLED, OrderStatus.FAILED));
        long lowStockCount = inventoryRepository.countLowStock();
        long pendingReviews = reviewRepository.countByStatus(ReviewStatus.PENDING);
        long pendingReturns = returnRequestRepository.countByStatus(ReturnRequestStatus.REQUESTED);
        long openTickets = supportTicketRepository.countByStatus(SupportTicketStatus.OPEN);

        BigDecimal todayRev = Money.of(orderRepository.sumRevenueBetween(startOfToday, now));
        BigDecimal weekRev = Money.of(orderRepository.sumRevenueBetween(startOfWeek, now));
        BigDecimal monthRev = Money.of(orderRepository.sumRevenueBetween(startOfMonth, now));
        BigDecimal yearRev = Money.of(orderRepository.sumRevenueBetween(startOfYear, now));

        long todayOrders = orderRepository.countActiveOrdersBetween(startOfToday, now);
        long weekOrders = orderRepository.countActiveOrdersBetween(startOfWeek, now);
        long monthOrders = orderRepository.countActiveOrdersBetween(startOfMonth, now);
        long yearOrders = orderRepository.countActiveOrdersBetween(startOfYear, now);

        List<OrderSummaryDto> recentOrders = orderRepository.searchAdmin(
                        null, null, null, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "placedAt")))
                .getContent().stream().map(OrderSummaryDto::from).toList();

        List<ProfileDto> recentCustomers = customerService.searchCustomers(
                        null, null, PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "createdAt")))
                .content();

        List<TopProductStat> bestSelling = toProductStats(
                orderItemRepository.findTopSellingProductsBetween(startOfYear, now), 6);
        List<TopBrandStat> topBrandsYtd = toBrandStats(
                orderItemRepository.findTopBrandsBetween(startOfYear, now), 6);
        List<InventoryDto> lowStockItems = inventoryService.listLowStock().stream().limit(8).toList();

        List<PeriodBucket> last14Days = buildDailyBuckets(todayUtc.minusDays(13), todayUtc);
        List<PeriodBucket> last12Months = buildMonthlyBuckets(YearMonth.from(todayUtc).minusMonths(11), 12);
        List<PeriodBucket> last5Years = buildYearlyBuckets(todayUtc.getYear() - 4, 5);

        return new DashboardOverview(
                totalCustomers,
                totalProducts,
                activeProducts,
                totalOrders,
                totalRevenue,
                pendingOrders,
                completedOrders,
                cancelledOrders,
                lowStockCount,
                pendingReviews,
                pendingReturns,
                openTickets,
                todayRev,
                weekRev,
                monthRev,
                yearRev,
                todayOrders,
                weekOrders,
                monthOrders,
                yearOrders,
                recentOrders,
                recentCustomers,
                bestSelling,
                topBrandsYtd,
                lowStockItems,
                last14Days,
                last12Months,
                last5Years
        );
    }

    @Transactional(readOnly = true)
    public SalesReport generateSalesReport(LocalDate fromDate, LocalDate toDate) {
        LocalDate end = toDate != null ? toDate : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = fromDate != null ? fromDate : end.minusDays(29);
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long orderCount = orderRepository.countActiveOrdersBetween(from, to);
        BigDecimal revenue = Money.of(orderRepository.sumRevenueBetween(from, to));
        BigDecimal aov = orderCount > 0
                ? revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP)
                : Money.ZERO;
        BigDecimal refundedTotal = Money.of(refundRepository.sumAmountByStatusBetween(RefundStatus.PROCESSED, from, to));
        long refundedCount = refundRepository.countByStatusBetween(RefundStatus.PROCESSED, from, to);
        long newCustomers = userRepository.countByRoleNameAndCreatedAtAfter(RoleName.CUSTOMER, from);

        List<PeriodBucket> buckets = buildDailyBuckets(start, end);
        List<TopProductStat> topProducts = toProductStats(
                orderItemRepository.findTopSellingProductsBetween(from, to), 10);
        List<TopBrandStat> topBrands = toBrandStats(
                orderItemRepository.findTopBrandsBetween(from, to), 10);
        List<CouponDto> coupons = couponRepository.findAll().stream().map(CouponDto::from).toList();

        return new SalesReport(
                from,
                to,
                orderCount,
                revenue,
                aov,
                refundedTotal,
                refundedCount,
                newCustomers,
                buckets,
                topProducts,
                topBrands,
                coupons
        );
    }

    private List<PeriodBucket> buildDailyBuckets(LocalDate start, LocalDate end) {
        List<PeriodBucket> list = new ArrayList<>();
        LocalDate cur = start;
        int guard = 0;
        while (!cur.isAfter(end) && guard++ < 90) {
            Instant dFrom = cur.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant dTo = cur.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            long count = orderRepository.countActiveOrdersBetween(dFrom, dTo);
            BigDecimal rev = Money.of(orderRepository.sumRevenueBetween(dFrom, dTo));
            list.add(new PeriodBucket(cur.toString(), dFrom, dTo, count, rev));
            cur = cur.plusDays(1);
        }
        return list;
    }

    private List<PeriodBucket> buildMonthlyBuckets(YearMonth startMonth, int countMonths) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        List<PeriodBucket> list = new ArrayList<>();
        for (int i = 0; i < countMonths; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            Instant mFrom = ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant mTo = ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            long count = orderRepository.countActiveOrdersBetween(mFrom, mTo);
            BigDecimal rev = Money.of(orderRepository.sumRevenueBetween(mFrom, mTo));
            list.add(new PeriodBucket(ym.format(fmt), mFrom, mTo, count, rev));
        }
        return list;
    }

    private List<PeriodBucket> buildYearlyBuckets(int startYear, int countYears) {
        List<PeriodBucket> list = new ArrayList<>();
        for (int i = 0; i < countYears; i++) {
            int yr = startYear + i;
            Instant yFrom = LocalDate.of(yr, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant yTo = LocalDate.of(yr + 1, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
            long count = orderRepository.countActiveOrdersBetween(yFrom, yTo);
            BigDecimal rev = Money.of(orderRepository.sumRevenueBetween(yFrom, yTo));
            list.add(new PeriodBucket(String.valueOf(yr), yFrom, yTo, count, rev));
        }
        return list;
    }

    private static List<TopProductStat> toProductStats(List<Object[]> rows, int limit) {
        return rows.stream().limit(limit).map(r -> new TopProductStat(
                r[0] != null ? ((Number) r[0]).longValue() : null,
                (String) r[1],
                (String) r[2],
                r[3] != null ? ((Number) r[3]).longValue() : 0L,
                r[4] instanceof BigDecimal bd ? Money.of(bd) : Money.ZERO
        )).toList();
    }

    private static List<TopBrandStat> toBrandStats(List<Object[]> rows, int limit) {
        return rows.stream().limit(limit).map(r -> new TopBrandStat(
                (String) r[0],
                r[1] != null ? ((Number) r[1]).longValue() : 0L,
                r[2] instanceof BigDecimal bd ? Money.of(bd) : Money.ZERO
        )).toList();
    }
}
