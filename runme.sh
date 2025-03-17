#!/bin/bash

# Set the root directory
ROOT_DIR=$(pwd)
COMPILED_DIR="$ROOT_DIR/compiled"
LIB_DIR="$ROOT_DIR/lib"

# Ensure the compiled directory exists
mkdir -p "$COMPILED_DIR"

# Compilation function
compile() {
    # Ensure the compiled directory exists
    mkdir -p "$COMPILED_DIR/UserService"
    mkdir -p "$COMPILED_DIR/ProductService"
    mkdir -p "$COMPILED_DIR/OrderService"

    # Compile each service with dependencies
    cd "$ROOT_DIR/src/OrderService" && javac -cp "$LIB_DIR/*" -d "$COMPILED_DIR/OrderService" OrderService.java && cd "$ROOT_DIR"
    cd "$ROOT_DIR/src/UserService" && javac -cp "$LIB_DIR/*" -d "$COMPILED_DIR/UserService" UserService.java && cd "$ROOT_DIR"
    cd "$ROOT_DIR/src/ProductService" && javac -cp "$LIB_DIR/*" -d "$COMPILED_DIR/ProductService" ProductService.java && cd "$ROOT_DIR"

    # Copy config files
    if [ -f "$ROOT_DIR/config.json" ]; then
        cp "$ROOT_DIR/config.json" "$COMPILED_DIR/UserService"
        cp "$ROOT_DIR/config.json" "$COMPILED_DIR/ProductService"
        cp "$ROOT_DIR/config.json" "$COMPILED_DIR/OrderService"
    else
        echo "Warning: config.json not found in root directory!"
    fi
}

mongo_status_check() {
    echo "Starting MongoDB..."
    docker-compose up -d # Start if not already started
}

# Start services
start_user_service() {
    mongo_status_check
    cd "$COMPILED_DIR/UserService"
    java -cp ".:$LIB_DIR/*" UserService config.json
}

start_product_service() {
    cd "$COMPILED_DIR/ProductService" && java -cp ".:$LIB_DIR/*" ProductService config.json
}

start_order_service() {
    cd "$COMPILED_DIR/OrderService" && java -cp ".:$LIB_DIR/*" OrderService config.json
}

start_workload() {
    if [ -z "$2" ]; then
        echo "Usage: ./runme.sh -w <workload_file>"
        exit 1
    fi
    python3 "$ROOT_DIR/parser.py" "$2"
}

# Argument handling
case "$1" in
    -c)
        compile
        ;;
    -u)
        start_user_service
        ;;
    -p)
        start_product_service
        ;;
    -o)
        start_order_service
        ;;
    -w)
        start_workload "$@"
        ;;
    *)
        echo "Usage: ./runme.sh -c | -u | -p | -o | -w <workload_file>"
        exit 1
        ;;
esac
