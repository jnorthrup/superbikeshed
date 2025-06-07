#!/bin/bash

cd   ~/mpdata/
find import/ -mindepth 4 -maxdepth 4 -type d |cut  -d / -f4-5
