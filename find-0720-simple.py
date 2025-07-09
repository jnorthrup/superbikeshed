#!/usr/bin/env python3

import requests

url = "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"

# Get file size
head = requests.head(url)
size = int(head.headers['Content-Length'])
print(f"Archive size: {size:,} bytes ({size/1024/1024/1024:.1f} GB)")

# Get last 64KB
print("\nFetching last 64KB...")
headers = {'Range': f'bytes={size - 65536}-'}
resp = requests.get(url, headers=headers)

# Search for filenames containing 0720
data = resp.content
i = 0
found = []

while i < len(data) - 46:
    # Look for central directory signature
    if data[i:i+4] == b'PK\x01\x02':
        name_len = data[i+28] | (data[i+29] << 8)
        if i + 46 + name_len <= len(data):
            try:
                filename = data[i+46:i+46+name_len].decode('utf-8')
                if '0720' in filename:
                    found.append(filename)
                    print(f"FOUND: {filename}")
            except:
                pass
        # Move to next entry
        extra_len = data[i+30] | (data[i+31] << 8)
        comment_len = data[i+32] | (data[i+33] << 8)
        i += 46 + name_len + extra_len + comment_len
    else:
        i += 1

print(f"\nTotal files with '0720': {len(found)}")