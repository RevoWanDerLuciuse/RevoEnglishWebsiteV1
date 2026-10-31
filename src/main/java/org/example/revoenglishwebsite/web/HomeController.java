package org.example.revoenglishwebsite.web;

import org.example.revoenglishwebsite.pricing.PricingCatalog;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final PricingCatalog pricing;

    public HomeController(PricingCatalog pricing) {
        this.pricing = pricing;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pricingOffers", pricing.offers(null));
        return "index";
    }
}
