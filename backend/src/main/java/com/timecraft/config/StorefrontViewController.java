package com.timecraft.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Clean vanity URL routing for the Vanilla HTML/CSS/JS customer storefront.
 * Forwards clean paths (/shop, /products/{slug}, /cart, /checkout, /login, /register,
 * /verify-otp, /account/**) to static HTML pages without any SPA framework.
 */
@Controller
public class StorefrontViewController {

    @GetMapping("/shop")
    public String shop() {
        return "forward:/pages/shop.html";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug) {
        return "forward:/pages/product-details.html";
    }

    @GetMapping("/cart")
    public String cart() {
        return "forward:/pages/cart.html";
    }

    @GetMapping("/checkout")
    public String checkout() {
        return "forward:/pages/checkout.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/pages/login.html";
    }

    @GetMapping("/register")
    public String register() {
        return "forward:/pages/register.html";
    }

    @GetMapping("/verify-otp")
    public String verifyOtp() {
        return "forward:/pages/verify-otp.html";
    }

    @GetMapping("/account")
    public String account() {
        return "forward:/pages/profile.html";
    }

    @GetMapping("/account/orders")
    public String accountOrders() {
        return "forward:/pages/orders.html";
    }

    @GetMapping("/account/addresses")
    public String accountAddresses() {
        return "forward:/pages/addresses.html";
    }

    @GetMapping("/wishlist")
    public String wishlist() {
        return "forward:/pages/wishlist.html";
    }

    @GetMapping("/compare")
    public String compare() {
        return "forward:/pages/compare.html";
    }

    @GetMapping("/about")
    public String about() {
        return "forward:/pages/about.html";
    }
}
