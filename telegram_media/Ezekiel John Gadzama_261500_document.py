#!/usr/bin/env python3
"""
PancakeSwap trading bot - hardcoded version (NOT SECURE).
"""

import asyncio
import json
import time
from decimal import Decimal, getcontext
from web3 import Web3
from eth_account import Account

# High precision for decimals
getcontext().prec = 60

# -----------------------
# Hardcoded config
# -----------------------
BSC_RPC_URL = "https://bsc-dataseed.binance.org/"
PANCAKE_ROUTER_ADDRESS = "0x10ED43C718714eb63d5aA57B78B54704E256024E"  # PancakeSwap v2
WBNB_ADDRESS = "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c"  # WBNB

# Replace this with your TAKE token contract address
TAKE_TOKEN_ADDRESS = "0xe747e54783ba3f77a8e5251a3cba19ebe9c0e197"

# Replace this with your real 12-word mnemonic
MNEMONIC = "mosquito supply leisure lava divert veteran coyote army few uphold ginger moon"

# Trade settings
SUPPORT_FEE_ON_TRANSFER = True
SLIPPAGE = Decimal("0.05")
TRADE_INTERVAL_SECONDS = 60
SMALL_TRADE_FRACTION = Decimal("0.01")
GAS_PRICE_GWEI = Decimal("0.13")

# -----------------------
# ABI snippets
# -----------------------
PANCAKE_ROUTER_ABI = json.loads("""[
  {"constant":false,"inputs":[{"name":"amountIn","type":"uint256"},{"name":"amountOutMin","type":"uint256"},{"name":"path","type":"address[]"},{"name":"to","type":"address"},{"name":"deadline","type":"uint256"}],"name":"swapExactTokensForTokens","outputs":[{"name":"amounts","type":"uint256[]"}],"type":"function"},
  {"constant":false,"inputs":[{"name":"amountIn","type":"uint256"},{"name":"amountOutMin","type":"uint256"},{"name":"path","type":"address[]"},{"name":"to","type":"address"},{"name":"deadline","type":"uint256"}],"name":"swapExactTokensForTokensSupportingFeeOnTransferTokens","outputs":[],"type":"function"},
  {"constant":true,"inputs":[{"name":"amountIn","type":"uint256"},{"name":"path","type":"address[]"}],"name":"getAmountsOut","outputs":[{"name":"amounts","type":"uint256[]"}],"type":"function"}
]""")

TOKEN_ABI = json.loads("""[
  {"constant":true,"inputs":[{"name":"account","type":"address"}],"name":"balanceOf","outputs":[{"name":"","type":"uint256"}],"type":"function"},
  {"constant":false,"inputs":[{"name":"spender","type":"address"},{"name":"amount","type":"uint256"}],"name":"approve","outputs":[{"name":"","type":"bool"}],"type":"function"},
  {"constant":true,"inputs":[],"name":"decimals","outputs":[{"name":"","type":"uint8"}],"type":"function"},
  {"constant":true,"inputs":[{"name":"owner","type":"address"},{"name":"spender","type":"address"}],"name":"allowance","outputs":[{"name":"","type":"uint256"}],"type":"function"}
]""")

# -----------------------
# Connect
# -----------------------
w3 = Web3(Web3.HTTPProvider(BSC_RPC_URL))
if not w3.is_connected():
    raise SystemExit("Could not connect to BSC node.")

PANCAKE_ROUTER_ADDRESS = Web3.to_checksum_address(PANCAKE_ROUTER_ADDRESS)
WBNB_ADDRESS = Web3.to_checksum_address(WBNB_ADDRESS)
TAKE_TOKEN_ADDRESS = Web3.to_checksum_address(TAKE_TOKEN_ADDRESS)

router_contract = w3.eth.contract(address=PANCAKE_ROUTER_ADDRESS, abi=PANCAKE_ROUTER_ABI)
wbnb_contract = w3.eth.contract(address=WBNB_ADDRESS, abi=TOKEN_ABI)
take_contract = w3.eth.contract(address=TAKE_TOKEN_ADDRESS, abi=TOKEN_ABI)

# -----------------------
# Account
# -----------------------
Account.enable_unaudited_hdwallet_features()
account = Account.from_mnemonic(MNEMONIC)
PRIVATE_KEY = account.key.hex()
ADDRESS = account.address

print("PancakeSwap Trading Bot - Hardcoded Version")
print(f"Connected: {w3.is_connected()}   Account: {ADDRESS}")

# -----------------------
# Helpers
# -----------------------
MAX_UINT256 = 2**256 - 1

def to_token_units(amount: Decimal, decimals: int) -> int:
    return int((amount * (Decimal(10) ** decimals)).to_integral_value(rounding="ROUND_FLOOR"))

def from_token_units(amount_int: int, decimals: int) -> Decimal:
    return Decimal(amount_int) / (Decimal(10) ** decimals)

def wait_for_transaction_receipt(tx_hash, timeout=180):
    start = time.time()
    while time.time() - start < timeout:
        try:
            receipt = w3.eth.get_transaction_receipt(tx_hash)
            if receipt:
                return receipt
        except:
            pass
        time.sleep(2)
    raise Exception("Tx not confirmed")

def get_token_balance(token_contract, addr):
    bal = token_contract.functions.balanceOf(addr).call()
    try:
        dec = token_contract.functions.decimals().call()
    except:
        dec = 18
    return bal, dec

def get_token_allowance(token_contract, owner, spender):
    try:
        return token_contract.functions.allowance(owner, spender).call()
    except:
        return 0

def ensure_approval(token_contract, owner, spender, amount, privkey):
    allowance = get_token_allowance(token_contract, owner, spender)
    if allowance >= amount:
        return None
    print(f"Approving {token_contract.address}...")
    acct = Account.from_key(privkey)
    nonce = w3.eth.get_transaction_count(acct.address)
    tx = token_contract.functions.approve(spender, MAX_UINT256).build_transaction({
        "from": acct.address,
        "gas": 120000,
        "gasPrice": w3.to_wei(str(GAS_PRICE_GWEI), "gwei"),
        "nonce": nonce,
    })
    signed = w3.eth.account.sign_transaction(tx, privkey)
    raw = w3.eth.send_raw_transaction(signed["raw_transaction"])
    receipt = wait_for_transaction_receipt(raw)
    if receipt["status"] != 1:
        raise Exception("Approval failed")
    print("Approval confirmed.")

# -----------------------
# Swap
# -----------------------
async def swap_tokens(amount_in, path, privkey, support_fee=SUPPORT_FEE_ON_TRANSFER):
    acct = Account.from_key(privkey)
    deadline = int(time.time()) + 1200
    ensure_approval(w3.eth.contract(address=path[0], abi=TOKEN_ABI), acct.address, PANCAKE_ROUTER_ADDRESS, amount_in, privkey)

    try:
        amounts = router_contract.functions.getAmountsOut(amount_in, path).call()
        amount_out_min = int(Decimal(amounts[-1]) * (1 - SLIPPAGE))
    except:
        amount_out_min = int(amount_in * 0.5)

    nonce = w3.eth.get_transaction_count(acct.address)
    if support_fee:
        tx_fn = router_contract.functions.swapExactTokensForTokensSupportingFeeOnTransferTokens(
            amount_in, amount_out_min, path, acct.address, deadline
        )
    else:
        tx_fn = router_contract.functions.swapExactTokensForTokens(
            amount_in, amount_out_min, path, acct.address, deadline
        )

    tx = tx_fn.build_transaction({
        "from": acct.address,
        "gas": 400000,
        "gasPrice": w3.to_wei(str(GAS_PRICE_GWEI), "gwei"),
        "nonce": nonce,
    })
    signed = w3.eth.account.sign_transaction(tx, privkey)
    raw = w3.eth.send_raw_transaction(signed["raw_transaction"])
    receipt = wait_for_transaction_receipt(raw)
    if receipt["status"] != 1:
        raise Exception("Swap failed")
    print(f"Swap confirmed in block {receipt['blockNumber']}")
    return w3.to_hex(raw)

# -----------------------
# Balances
# -----------------------
def print_balances():
    bnb = Decimal(w3.eth.get_balance(ADDRESS)) / (10**18)
    wbnb_raw, wbnb_dec = get_token_balance(wbnb_contract, ADDRESS)
    take_raw, take_dec = get_token_balance(take_contract, ADDRESS)
    print("\n=== Balances ===")
    print(f"BNB: {bnb}")
    print(f"WBNB: {from_token_units(wbnb_raw, wbnb_dec)}")
    print(f"TAKE: {from_token_units(take_raw, take_dec)}")
    print("==============\n")

# -----------------------
# Main loop
# -----------------------
async def main_loop():
    print_balances()
    direction = 0
    while True:
        wbnb_raw, wbnb_dec = get_token_balance(wbnb_contract, ADDRESS)
        take_raw, take_dec = get_token_balance(take_contract, ADDRESS)
        wbnb_amt = from_token_units(wbnb_raw, wbnb_dec)
        take_amt = from_token_units(take_raw, take_dec)

        if direction == 0 and wbnb_amt > 0.001:
            trade_amt = wbnb_amt * SMALL_TRADE_FRACTION
            amt_in = to_token_units(trade_amt, wbnb_dec)
            print(f"Swapping {trade_amt} WBNB -> TAKE")
            await swap_tokens(amt_in, [WBNB_ADDRESS, TAKE_TOKEN_ADDRESS], PRIVATE_KEY)
            direction = 1
        elif direction == 1 and take_amt > 1:
            trade_amt = take_amt * SMALL_TRADE_FRACTION
            amt_in = to_token_units(trade_amt, take_dec)
            print(f"Swapping {trade_amt} TAKE -> WBNB")
            await swap_tokens(amt_in, [TAKE_TOKEN_ADDRESS, WBNB_ADDRESS], PRIVATE_KEY)
            direction = 0
        else:
            print("No trade conditions met.")

        print_balances()
        await asyncio.sleep(TRADE_INTERVAL_SECONDS)

if __name__ == "__main__":
    asyncio.run(main_loop())
