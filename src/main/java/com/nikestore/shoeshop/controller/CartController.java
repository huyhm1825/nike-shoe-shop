package com.nikestore.shoeshop.controller;

import com.nikestore.shoeshop.dto.CheckoutForm;
import com.nikestore.shoeshop.entity.CustomerOrder;
import com.nikestore.shoeshop.entity.Product;
import com.nikestore.shoeshop.repository.AppUserRepository;
import com.nikestore.shoeshop.service.CartService;
import com.nikestore.shoeshop.service.CatalogService;
import com.nikestore.shoeshop.service.CouponService;
import com.nikestore.shoeshop.service.OrderService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class CartController {

    private static final String COUPON_KEY = "CART_COUPON";

    private final CartService cartService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final AppUserRepository userRepository;
    private final CouponService couponService;

    public CartController(CartService cartService,
                          CatalogService catalogService,
                          OrderService orderService,
                          AppUserRepository userRepository,
                          CouponService couponService) {
        this.cartService = cartService;
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.userRepository = userRepository;
        this.couponService = couponService;
    }

    @PostMapping("/cart/add")
    public String addToCart(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "42") String size,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        var product = catalogService.requireProductById(productId);
        if (product.getStock() == null || product.getStock() <= 0) {
            redirectAttributes.addFlashAttribute("error", "This product is currently out of stock.");
            return "redirect:/product/" + product.getSlug();
        }
        if (!isValidSize(product, size)) {
            redirectAttributes.addFlashAttribute("error", "Selected size is not available for this product.");
            return "redirect:/product/" + product.getSlug();
        }
        if (quantity < 1) {
            redirectAttributes.addFlashAttribute("error", "Quantity must be at least 1.");
            return "redirect:/product/" + product.getSlug();
        }
        int cappedQuantity = Math.min(quantity, product.getStock());
        cartService.add(session, product, cappedQuantity, size);
        if (quantity > product.getStock()) {
            redirectAttributes.addFlashAttribute("info", "Quantity was adjusted to available stock.");
        }
        redirectAttributes.addFlashAttribute("success", "Added to cart successfully!");
        return "redirect:/cart";
    }

    @GetMapping("/cart")
    public String cart(HttpSession session, Model model) {
        double subtotal = cartService.total(session);
        String couponCode = couponCode(session);
        CouponService.CouponQuote couponQuote = couponService.quote(couponCode, subtotal).orElse(null);
        populateSummary(model, session, subtotal, couponCode, couponQuote);
        var cartItems = cartService.items(session).values();
        model.addAttribute("cartItems", cartItems);
        Map<Long, List<String>> sizeOptionsByProductId = cartItems.stream()
                .map(item -> catalogService.requireProductById(item.getProductId()))
                .collect(Collectors.toMap(
                        product -> product.getId(),
                        catalogService::availableSizes,
                        (first, second) -> first
                ));
        model.addAttribute("sizeOptionsByProductId", sizeOptionsByProductId);
        return "shop/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Long productId, @RequestParam int quantity, @RequestParam String size, @RequestParam String oldSize, HttpSession session, RedirectAttributes redirectAttributes) {
        var product = catalogService.requireProductById(productId);
        if (product.getStock() == null || product.getStock() <= 0) {
            cartService.remove(session, productId, oldSize);
            redirectAttributes.addFlashAttribute("error", "This product is out of stock and was removed from your cart.");
            return "redirect:/cart";
        }
        if (!isValidSize(product, size)) {
            redirectAttributes.addFlashAttribute("error", "Selected size is not available for this product.");
            return "redirect:/cart";
        }
        if (quantity < 1) {
            redirectAttributes.addFlashAttribute("error", "Quantity must be at least 1.");
            return "redirect:/cart";
        }
        int cappedQuantity = Math.min(quantity, product.getStock());
        cartService.update(session, productId, cappedQuantity, oldSize, size, product);
        if (quantity > product.getStock()) {
            redirectAttributes.addFlashAttribute("info", "Quantity was adjusted to available stock.");
        }
        redirectAttributes.addFlashAttribute("success", "Cart updated successfully!");
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeCart(@RequestParam Long productId, @RequestParam String size, HttpSession session, RedirectAttributes redirectAttributes) {
        cartService.remove(session, productId, size);
        redirectAttributes.addFlashAttribute("info", "Item removed from cart.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/coupon/apply")
    public String applyCoupon(@RequestParam String couponCode, HttpSession session, RedirectAttributes redirectAttributes) {
        if (couponCode != null && !couponCode.isBlank()) {
            session.setAttribute(COUPON_KEY, couponCode.trim().toUpperCase());
            redirectAttributes.addFlashAttribute("success", "Coupon applied successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Please enter a coupon code.");
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/coupon/clear")
    public String clearCoupon(HttpSession session, RedirectAttributes redirectAttributes) {
        session.removeAttribute(COUPON_KEY);
        redirectAttributes.addFlashAttribute("info", "Coupon removed.");
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model, Authentication authentication, RedirectAttributes redirectAttributes) {
        // Require login
        if (authentication == null || !authentication.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("error", "Please login to proceed with checkout.");
            return "redirect:/signin";
        }
        if (cartService.count(session) == 0) {
            return "redirect:/shop";
        }
        CheckoutForm form = new CheckoutForm();
        var user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user != null) {
            form.setCustomerName(user.getFullName());
            form.setPhone(user.getPhone() != null ? user.getPhone() : "");
            form.setAddress(user.getAddress() != null ? user.getAddress() : "");
        }
        form.setCouponCode(couponCode(session));

        double subtotal = cartService.total(session);
        CouponService.CouponQuote couponQuote = couponService.quote(form.getCouponCode(), subtotal).orElse(null);

        model.addAttribute("form", form);
        model.addAttribute("cartItems", cartService.items(session).values());
        populateSummary(model, session, subtotal, form.getCouponCode(), couponQuote);
        return "shop/checkout";
    }

    @PostMapping("/checkout")
    public String placeOrder(@Valid @ModelAttribute("form") CheckoutForm form,
                             BindingResult bindingResult,
                             HttpSession session,
                             Authentication authentication,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        // Require login
        if (authentication == null || !authentication.isAuthenticated()) {
            redirectAttributes.addFlashAttribute("error", "Please login to place an order.");
            return "redirect:/signin";
        }
        if (cartService.count(session) == 0) {
            return "redirect:/shop";
        }
        double subtotal = cartService.total(session);
        CouponService.CouponQuote couponQuote = couponService.quote(form.getCouponCode(), subtotal).orElse(null);
        if (form.getCouponCode() != null && !form.getCouponCode().isBlank() && couponQuote == null) {
            bindingResult.rejectValue("couponCode", "invalid", "Coupon is invalid or not eligible for this order.");
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("cartItems", cartService.items(session).values());
            populateSummary(model, session, subtotal, form.getCouponCode(), couponQuote);
            return "shop/checkout";
        }
        String email = authentication.getName();
        CustomerOrder order = orderService.createOrder(form, cartService.items(session), email, couponQuote);
        cartService.clear(session);
        session.removeAttribute(COUPON_KEY);
        redirectAttributes.addFlashAttribute("success", "Order placed successfully! Order code: " + order.getOrderCode());
        return "redirect:/checkout/success/" + order.getOrderCode();
    }

    @GetMapping("/checkout/success/{orderCode}")
    @Transactional(readOnly = true)
    public String success(@PathVariable String orderCode, Model model) {
        CustomerOrder order = orderService.requireOrderByCode(orderCode);
        // Force load items trong transaction
        if (order.getItems() != null) {
            order.getItems().size();
        }
        model.addAttribute("order", order);
        return "shop/order-success";
    }

    private String couponCode(HttpSession session) {
        Object raw = session.getAttribute(COUPON_KEY);
        return raw == null ? "" : raw.toString();
    }

    private void populateSummary(Model model, HttpSession session, double subtotal, String couponCode, CouponService.CouponQuote couponQuote) {
        double discount = couponQuote != null ? couponQuote.getDiscountAmount() : 0d;
        double grandTotal = Math.max(0d, subtotal - discount);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("discountAmount", discount);
        model.addAttribute("grandTotal", grandTotal);
        model.addAttribute("couponCode", couponCode);
        model.addAttribute("couponQuote", couponQuote);
        model.addAttribute("cartCount", cartService.count(session));
    }

    private boolean isValidSize(Product product, String size) {
        List<String> availableSizes = catalogService.availableSizes(product);
        return availableSizes.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(size == null ? "" : size.trim()));
    }
}
