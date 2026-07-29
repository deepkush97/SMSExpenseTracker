#!/bin/bash
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Checking for running emulator...${NC}"

DEVICE=$(adb devices | grep -E "^emulator-[0-9]+\s+device$" | head -1 | awk '{print $1}')

if [ -z "$DEVICE" ]; then
    echo -e "${RED}No running emulator found.${NC}"
    echo "Start an emulator first, then re-run this script."
    exit 1
fi

echo -e "${GREEN}Found emulator: $DEVICE${NC}"
echo ""

send_sms() {
    local sender="$1"
    local body="$2"
    echo -e "${GREEN}Sending to $DEVICE from $sender...${NC}"
    adb -s "$DEVICE" emu sms send "$sender" "$body"
    echo ""
}

# ── HDFC Bank (sender: AD-HDFCBK-S) ──

send_sms "AD-HDFCBK-S" \
"Spent Rs.4831.76 On HDFC Bank Card 1111 At Acme Inc. On 2026-07-26:21:35:51.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 2468 to 7308080808"

send_sms "AD-HDFCBK-S" \
"Txn Rs.25.00
On HDFC Bank Card 1111
At Q123456789@ybl
by UPI 620436716168
On 23-07
Not You?
Call 18002586161/SMS BLOCK CC 2468 to 7308080808"

send_sms "AD-HDFCBK-S" \
"Alert! Rs. 32 refunded by someComp on 20/JUL/2026 & adjusted against HDFC Bank Credit Card 1111 View updated balance here: bank link"

send_sms "AD-HDFCBK-S" \
"Credit Alert!
Rs.12000.00 credited to HDFC Bank A/c XX1111 on 18-07-26 from VPA yourupi@addr (UPI 656540994008)"

send_sms "AD-HDFCBK-S" \
"PAYMENT ALERT!
INR 1000.00 deducted from HDFC Bank A/C No 1234 towards Some CORP UMRN: HDFC7011403241000251"

send_sms "AD-HDFCBK-S" \
"Payment Successful! Rs. 66093.00 from A/c **********1233 to SOMECORP via HDFC Bank NetBanking. Not you?Call 18002586161"

send_sms "AD-HDFCBK-S" \
"Update! INR 1,000.00 deposited in HDFC Bank A/c XX1233 on 31-MAR-26 for NEFT Cr-ICIC0099999-SOMECOMPANY-someName.Avl bal INR 1,01,000.95. Cheque deposits in A/C are subject to clearing"

# ── ICICI Bank (sender: AD-ICICIT-S) ──

send_sms "AD-ICICIT-S" \
"ICICI Bank Acct XX123 debited for Rs 242.00 on 26-Jul-26; BUS Ticket credited. UPI:003637672623. Call 18002662 for dispute. SMS BLOCK 796 to 9215676766."

send_sms "AD-ICICIT-S" \
"Dear Customer, Acct XX123 is credited with Rs 20.00 on 19-Jul-26 from NPCI BHIM. UPI:103691213332-ICICI Bank."

send_sms "AD-ICICIT-S" \
"ICICI Bank Account XX123 is credited with Rs 61,000.00 on 03-Jul-26 by Account linked to mobile number XXXXX01234. IMPS Ref. no. 618441385660."

# ── DCB Bank (sender: JD-DCBANK-T) ──

send_sms "JD-DCBANK-T" \
"INR 1403.36 debited DCB Bank a/c*1234 POS/Ecom txn to cafe de lar on 19-06-2026 07:59 PM.Not you?Call 0226899777 or SMS BLOCKCARD <Last 4 digits>to 9821878789"

# ── Pluxee (sender: VD-Pluxee-S) ──

send_sms "VD-Pluxee-S" \
"Rs. 546.00 spent from Pluxee  Meal Card wallet, card no.xx4910 on 28-06-2026 21:38:47 at SWIGGY . Avl bal Rs.9388.14. Not you call 18002106919"

send_sms "VD-Pluxee-S" \
"Your Pluxee Card xx4910 has been credited with INR 546.00 on Sun Jun 28 2026 22:41:31as a reversal against a previous transaction on Jun 28,2026 21:38:47."

send_sms "VD-Pluxee-S" \
"Your Pluxee Card has been successfully credited with Rs.2200 towards  Meal Wallet on Thu Sep 05 2024 17:03:06. Your current Meal Wallet balance is Rs.7142.70."

echo -e "${GREEN}All 14 SMS messages pushed successfully to $DEVICE${NC}"
