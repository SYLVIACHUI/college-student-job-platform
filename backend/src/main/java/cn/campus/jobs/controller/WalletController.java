package cn.campus.jobs.controller;

import cn.campus.jobs.service.AuthService;
import cn.campus.jobs.service.WalletService;

import java.math.BigDecimal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class WalletController {
    public record Amount(@NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer=6,fraction=2) BigDecimal amount,
        @NotNull @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}
    public record Cancel(@NotNull @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey) {}
    private final AuthService auth;private final WalletService wallets;
    public WalletController(AuthService auth,WalletService wallets){this.auth=auth;this.wallets=wallets;}
    private String id(HttpServletRequest req){return auth.current(req).get("id").toString();}
    @GetMapping("/wallet") public Object wallet(@RequestParam(defaultValue="0") int page,HttpServletRequest req){return wallets.summary(id(req),page);}
    @PostMapping("/wallet/top-up") public Object topup(@Valid @RequestBody Amount input,HttpServletRequest req){return wallets.topUp(id(req),input);}
    @PostMapping("/applications/{applicationId}/payment") public Object pay(@PathVariable String applicationId,@Valid @RequestBody Amount input,HttpServletRequest req){return wallets.pay(id(req),applicationId,input);}
    @PostMapping("/wallet/withdrawals") public Object withdraw(@Valid @RequestBody Amount input,HttpServletRequest req){return wallets.withdraw(id(req),input);}
    @PostMapping("/wallet/withdrawals/{withdrawalId}/cancel") public Object cancel(@PathVariable String withdrawalId,@Valid @RequestBody Cancel input,HttpServletRequest req){return wallets.cancel(id(req),withdrawalId,input.requestKey());}
}
