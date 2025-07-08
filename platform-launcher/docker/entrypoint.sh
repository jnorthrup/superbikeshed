#!/bin/bash

# Start SSH daemon
/usr/sbin/sshd

# Keep container running
exec "$@"