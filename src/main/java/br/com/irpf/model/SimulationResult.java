package br.com.irpf.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public record SimulationResult(
        BigDecimal rendimentos,
        BigDecimal deducoes,
        BigDecimal baseCalculo,
        BigDecimal imposto,
        BigDecimal aliquotaEfetiva
) {
    public BigDecimal aliquotaEfetivaCalculada() {
        if (rendimentos == null || rendimentos.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return imposto
                .multiply(BigDecimal.valueOf(100))
                .divide(rendimentos, new MathContext(10))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return String.format(
                "rend=%s ded=%s base=%s imp=%s aliq=%s%%",
                rendimentos, deducoes, baseCalculo, imposto, aliquotaEfetiva
        );
    }
}
