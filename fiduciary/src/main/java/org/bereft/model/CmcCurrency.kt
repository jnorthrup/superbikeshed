package org.bereft.model

import com.google.gson.internal.bind.util.ISO8601Utils
import org.bereft.runtime.couchUtil.has_id
import java.io.Serializable
import java.math.BigDecimal
import java.util.*

/**

|Currency| Currency Code|CoinMarketCap ID|
|---|---|---|
|United States Dollar ($)|USD|2781|
|Albanian Lek (L)|ALL|3526|
|Algerian Dinar (د.ج)|DZD|3537|
|Argentine Peso ($)|ARS|2821|
|Armenian Dram (֏)|AMD|3527|
|Australian Dollar ($)|AUD|2782|
|Azerbaijani Manat (₼)|AZN|3528|
|Bahraini Dinar (.د.ب)|BHD|3531|
|Bangladeshi Taka (৳)|BDT|3530|
|Belarusian Ruble (Br)|BYN|3533|
|Bermudan Dollar ($)|BMD|3532|
|Bolivian Boliviano (Bs.)|BOB|2832|
|Bosnia-Herzegovina Convertible Mark (KM)|BAM|3529|
|Brazilian Real (R$)|BRL|2783|
|Bulgarian Lev (лв)|BGN|2814|
|Cambodian Riel (៛)|KHR|3549|
|Canadian Dollar ($)|CAD|2784|
|Chilean Peso ($)|CLP|2786|
|Chinese Yuan (¥)|CNY|2787|
|Colombian Peso ($)|COP|2820|
|Costa Rican Colón (₡)|CRC|3534|
|Croatian Kuna (kn)|HRK|2815|
|Cuban Peso ($)|CUP|3535|
|Czech Koruna (Kč)|CZK|2788|
|Danish Krone (kr)|DKK|2789|
|Dominican Peso ($)|DOP|3536|
|Egyptian Pound (£)|EGP|3538|
|Euro (€)|EUR|2790|
|Georgian Lari (₾)|GEL|3539|
|Ghanaian Cedi (₵)|GHS|3540|
|Guatemalan Quetzal (Q)|GTQ|3541|
|Honduran Lempira (L)|HNL|3542|
|Hong Kong Dollar ($)|HKD|2792|
|Hungarian Forint (Ft)|HUF|2793|
|Icelandic Króna (kr)|ISK|2818|
|Indian Rupee (₹)|INR|2796|
|Indonesian Rupiah (Rp)|IDR|2794|
|Iranian Rial (﷼)|IRR|3544|
|Iraqi Dinar (ع.د)|IQD|3543|
|Israeli New Shekel (₪)|ILS|2795|
|Jamaican Dollar ($)|JMD|3545|
|Japanese Yen (¥)|JPY|2797|
|Jordanian Dinar (د.ا)|JOD|3546|
|Kazakhstani Tenge (₸)|KZT|3551|
|Kenyan Shilling (Sh)|KES|3547|
|Kuwaiti Dinar (د.ك)|KWD|3550|
|Kyrgystani Som (с)|KGS|3548|
|Lebanese Pound (ل.ل)|LBP|3552|
|Macedonian Denar (ден)|MKD|3556|
|Malaysian Ringgit (RM)|MYR|2800|
|Mauritian Rupee (₨)|MUR|2816|
|Mexican Peso ($)|MXN|2799|
|Moldovan Leu (L)|MDL|3555|
|Mongolian Tugrik (₮)|MNT|3558|
|Moroccan Dirham (د.م.)|MAD|3554|
|Myanma Kyat (Ks)|MMK|3557|
|Namibian Dollar ($)|NAD|3559|
|Nepalese Rupee (₨)|NPR|3561|
|New Taiwan Dollar (NT$)|TWD|2811|
|New Zealand Dollar ($)|NZD|2802|
|Nicaraguan Córdoba (C$)|NIO|3560|
|Nigerian Naira (₦)|NGN|2819|
|Norwegian Krone (kr)|NOK|2801|
|Omani Rial (ر.ع.)|OMR|3562|
|Pakistani Rupee (₨)|PKR|2804|
|Panamanian Balboa (B/.)|PAB|3563|
|Peruvian Sol (S/.)|PEN|2822|
|Philippine Peso (₱)|PHP|2803|
|Polish Złoty (zł)|PLN|2805|
|Pound Sterling (£)|GBP|2791|
|Qatari Rial (ر.ق)|QAR|3564|
|Romanian Leu (lei)|RON|2817|
|Russian Ruble (₽)|RUB|2806|
|Saudi Riyal (ر.س)|SAR|3566|
|Serbian Dinar (дин.)|RSD|3565|
|Singapore Dollar (S$)|SGD|2808|
|South African Rand (R)|ZAR|2812|
|South Korean Won (₩)|KRW|2798|
|South Sudanese Pound (£)|SSP|3567|
|Sovereign Bolivar (Bs.)|VES|3573|
|Sri Lankan Rupee (Rs)|LKR|3553|
|Swedish Krona ( kr)|SEK|2807|
|Swiss Franc (Fr)|CHF|2785|
|Thai Baht (฿)|THB|2809|
|Trinidad and Tobago Dollar ($)|TTD|3569|
|Tunisian Dinar (د.ت)|TND|3568|
|Turkish Lira (₺)|TRY|2810|
|Ugandan Shilling (Sh)|UGX|3570|
|Ukrainian Hryvnia (₴)|UAH|2824|
|United Arab Emirates Dirham (د.إ)|AED|2813|
|Uruguayan Peso ($)|UYU|3571|
|Uzbekistan Som (so'm)|UZS|3572|
|Vietnamese Dong (₫)|VND|2823|


Along with these four precious metals:

|Precious Metal|Currency Code|CoinMarketCap ID|
|---|---|---|
|Gold Troy Ounce|XAU|3575|
|Silver Troy Ounce|XAG|3574|
|Platinum Ounce|XPT|3577|
|Palladium Ounce|XPD|3576|

 Warning: Using CoinMarketCap IDs is always recommended as not all cryptocurrency symbols are unique. They can also change with a cryptocurrency rebrand. If a symbol is used the API will always default to the cryptocurrency with the highest market cap if there are multiple matches. Our convert parameter also defaults to fiat if a cryptocurrency symbol also matches a supported fiat currency. You may use the convenient /map endpoints to quickly find the corresponding CoinMarketCap ID for a cryptocurrency or exchange.


 */
data class CmcCurrency(
    val id: String,
    val name: String,
    val symbol: String,
    val rank: Long,
    val price_usd: BigDecimal,
    val price_btc: BigDecimal,
    val `24h_volume_usd`: BigDecimal,
    val market_cap_usd: BigDecimal,
    val available_supply: BigDecimal,
    val total_supply: BigDecimal,
    val percent_change_1h: Double,
    val percent_change_24h: Double,
    val percent_change_7d: Double,
    val last_updated: Long,
) : has_id, Serializable {
    override lateinit var _id: String

    init {
        _id = "$id:${ISO8601Utils.format(Date(last_updated * 1000))}"

    }
}


