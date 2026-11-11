# F1n NFC logger (step 1)

Throwaway experiment. Answers a payment terminal just far enough to read the
transaction amount, logs it, then fails the read on purpose. It cannot complete
a payment and never touches your card.

## Build
1. Android Studio: New Project, Empty Views Activity, Kotlin, package `com.f1n.logger`, min SDK 26.
2. Copy `AndroidManifest.xml`, `res/xml/apduservice.xml` and the two `.kt` files over the generated ones.
3. Add a string `app_name` = "F1n Logger" if the template did not.
4. Run on a real phone (emulators have no NFC).

## Test (carry your physical card as backup)
Set Settings > Connected devices > Contactless payments > "Use default over open app" to
"Except when another payment app is open". Open F1n Logger, then tap at each terminal:
Walmart self-checkout, Cafe Rio, a gas pump, a coffee shop. Repeat on the Samsung.

Record for each terminal:
- Does an `AMOUNT:` line appear, and is it right?
- What does the terminal show after the failed read?
- After closing F1n, does a normal Wallet tap still work right away?
- How did the cashier react?

Unverified until run: AID lists, the PDOL layout and the preferred-service setting
vary by terminal, phone and Android version.
