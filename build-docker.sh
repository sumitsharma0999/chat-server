#!/bin/bash
docker build node -t chatserver:1.0
docker build match-maker -t match-maker:1.0
