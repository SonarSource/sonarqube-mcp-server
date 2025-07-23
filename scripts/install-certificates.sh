#!/bin/sh

# Certificate installation script for SonarQube MCP Server

CERT_DIR="/usr/local/share/ca-certificates/"

if [ "$(ls -A "$CERT_DIR")" ]; then
    echo "Installing custom certificates from $CERT_DIR..."

    # Run as root via sudo
    sudo /usr/sbin/update-ca-certificates

    echo "Custom certificates installed successfully"
fi
