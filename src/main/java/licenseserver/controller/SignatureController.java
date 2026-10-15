package licenseserver.controller;

import licenseserver.signature.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Открытая часть ЭЦП: сертификат и алгоритм, по которым клиент проверяет подпись тикета. */
@RestController
@RequestMapping("/api/signature")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;

    @GetMapping("/certificate")
    public Map<String, String> certificate() {
        return Map.of(
                "algorithm", signatureService.algorithm(),
                "certificate", signatureService.certificatePem());
    }
}
