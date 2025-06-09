@file:DependsOn("org.javamoney:moneta:pom:1.3")

import org.javamoney.moneta.spi.MoneyUtils

println("getBigDecimal(1L): " + MoneyUtils.getBigDecimal(1L))
