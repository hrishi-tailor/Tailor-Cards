#!/usr/bin/env python3
"""
Interactive pokemontcg.io ID Backfill Script for Tailor Cards

Searches candidates for unlinked products by name, set, and card number.
Requires manual confirmation per match before persisting to the database.
Optionally triggers the nightly price snapshot job upon completion.
"""

import sys
import os
import getpass
import json
import urllib.request
import urllib.error
import base64

def make_request(url, method="GET", data=None, auth_header=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Accept", "application/json")
    if auth_header:
        req.add_header("Authorization", auth_header)
    if data is not None:
        req.add_header("Content-Type", "application/json")
        body = json.dumps(data).encode("utf-8")
        req.data = body
    
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            err_json = json.loads(err_body)
            return e.code, err_json
        except Exception:
            return e.code, {"error": err_body}
    except Exception as e:
        return 500, {"error": str(e)}

def main():
    base_url = os.environ.get("API_BASE_URL", "http://localhost:8080").rstrip("/")
    admin_user = os.environ.get("ADMIN_USERNAME", "admin")
    admin_pass = os.environ.get("ADMIN_PASSWORD")

    print("=================================================================")
    print(" Tailor Cards - Pokémon TCG ID Backfill & Snapshot CLI Tool")
    print("=================================================================")
    print(f"Target Backend: {base_url}")
    print(f"Admin User:     {admin_user}")

    if not admin_pass:
        admin_pass = getpass.getpass(f"Enter admin password for '{admin_user}': ")

    auth_token = base64.b64encode(f"{admin_user}:{admin_pass}".encode("utf-8")).decode("utf-8")
    auth_header = f"Basic {auth_token}"

    # 1. Fetch unlinked products
    print("\nFetching unlinked products lacking pokemontcg_id...")
    status, products = make_request(f"{base_url}/api/admin/products/unlinked", "GET", auth_header=auth_header)
    
    if status == 401 or status == 403:
        print(f"[ERROR] Authentication failed (HTTP {status}). Please verify admin credentials.")
        sys.exit(1)
    if status != 200 or not isinstance(products, list):
        print(f"[ERROR] Failed to fetch unlinked products: HTTP {status} - {products}")
        sys.exit(1)

    if not products:
        print("[INFO] All products already have a pokemontcg_id configured! Nothing to backfill.")
    else:
        print(f"[INFO] Found {len(products)} unlinked product(s).\n")
        
        linked_count = 0
        for idx, prod in enumerate(products, 1):
            p_id = prod.get("id")
            p_name = prod.get("name")
            p_set = prod.get("set", "") or ""
            p_num = prod.get("cardNumber", "") or ""
            p_price = prod.get("price")
            p_cond = prod.get("condition", "") or ""

            print("-" * 65)
            print(f"Product [{idx}/{len(products)}] ID: {p_id}")
            print(f"  Name:        {p_name}")
            print(f"  Set:         {p_set}")
            print(f"  Number:      {p_num}")
            print(f"  Condition:   {p_cond}")
            print(f"  Price (CAD): ${p_price}")
            print("-" * 65)

            # Query candidates
            cand_status, candidates = make_request(
                f"{base_url}/api/admin/products/{p_id}/candidates", "GET", auth_header=auth_header
            )

            if cand_status == 200 and isinstance(candidates, list) and candidates:
                print("Candidates found from pokemontcg.io:")
                for c_idx, c in enumerate(candidates, 1):
                    cid = c.get("cardId")
                    cname = c.get("name")
                    cset = c.get("setName", "")
                    cnum = c.get("cardNumber", "")
                    cp = c.get("marketPriceUsd")
                    cp_str = f"${cp:.2f} USD" if cp is not None else "No price"
                    print(f"  [{c_idx}] ID: {cid:12} | {cname} | Set: {cset} #{cnum} | Market: {cp_str}")
            else:
                print("No automatic candidate matches found on pokemontcg.io.")
                candidates = []

            # Prompt user
            while True:
                choice = input("\nSelect candidate [1-N], enter manual ID, [s]kip, or [q]uit: ").strip()
                if not choice or choice.lower() == 's':
                    print("Skipped.")
                    break
                if choice.lower() == 'q':
                    print("Exiting backfill process.")
                    sys.exit(0)

                chosen_id = None
                if choice.isdigit():
                    num = int(choice)
                    if 1 <= num <= len(candidates):
                        chosen_id = candidates[num - 1].get("cardId")
                    else:
                        print(f"Invalid candidate number. Choose between 1 and {len(candidates)}.")
                        continue
                else:
                    chosen_id = choice

                # Confirmation per match
                confirm = input(f"Confirm linking Product #{p_id} ('{p_name}') to '{chosen_id}'? [y/N]: ").strip().lower()
                if confirm == 'y':
                    put_status, put_resp = make_request(
                        f"{base_url}/api/admin/products/{p_id}/pokemontcg-id",
                        method="PUT",
                        data={"pokemontcgId": chosen_id},
                        auth_header=auth_header
                    )
                    if put_status == 200:
                        print(f"✓ [SUCCESS] Successfully linked Product #{p_id} to pokemontcg_id='{chosen_id}'.")
                        linked_count += 1
                        break
                    else:
                        print(f"✗ [ERROR] Failed to update product: HTTP {put_status} - {put_resp}")
                        break
                else:
                    print("Match rejected by user.")
                    break

        print(f"\nBackfill session ended. Successfully linked {linked_count} product(s).")

    # 2. Trigger snapshot sync job
    trigger_sync = input("\nTrigger on-demand price snapshot sync now? [y/N]: ").strip().lower()
    if trigger_sync == 'y':
        print("Triggering price snapshot job...")
        sync_status, sync_resp = make_request(
            f"{base_url}/api/admin/pricing/sync-snapshots",
            method="POST",
            auth_header=auth_header
        )
        if sync_status == 200:
            print(f"✓ [SUCCESS] Snapshot job completed: {sync_resp.get('message', sync_resp)}")
        else:
            print(f"✗ [ERROR] Failed to trigger snapshot sync: HTTP {sync_status} - {sync_resp}")

if __name__ == "__main__":
    main()
