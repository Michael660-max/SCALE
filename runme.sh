#!/bin/bash

# Set the root directory
ROOT_DIR=$(pwd)
COMPILED_DIR="$ROOT_DIR/compiled"

# Ensure the compiled directory exists
mkdir -p "$COMPILED_DIR"

# Compilation function
compile() {
    # Compile Java files
    # javac -d "$COMPILED_DIR" "$ROOT_DIR/src/OrderService/OrderService.java"
    # javac -d "$COMPILED_DIR" "$ROOT_DIR/src/UserService/UserService.java"
    # javac -d "$COMPILED_DIR" "$ROOT_DIR/src/ProductService/ProductService.java"

    cd "$ROOT_DIR/src/OrderService" && javac -d "$COMPILED_DIR/OrderService" OrderService.java && cd "$ROOT_DIR"
    cd "$ROOT_DIR/src/UserService" && javac -d "$COMPILED_DIR/UserService" UserService.java && cd "$ROOT_DIR"
    cd "$ROOT_DIR/src/ProductService" && javac -d "$COMPILED_DIR/ProductService" ProductService.java && cd "$ROOT_DIR"

    # If a 'src' folder was created inside 'compiled', move its contents up and delete it
    if [ -d "$COMPILED_DIR/src" ]; then
        mv "$COMPILED_DIR/src/"* "$COMPILED_DIR/"  # Move contents up
        rm -r "$COMPILED_DIR/src"                 # Remove 'src' folder
    fi

    if [ -f "$ROOT_DIR/config.json" ]; then
        cp "$ROOT_DIR/config.json" "$COMPILED_DIR/UserService"
        cp "$ROOT_DIR/config.json" "$COMPILED_DIR/ProductService"
        cp "$ROOT_DIR/config.json" "$COMPILED_DIR/OrderService"

    else
        echo "Warning: config.json not found in root directory!"
    fi
}

# Start services
start_user_service() {
    java -cp "$COMPILED_DIR/UserService" UserService config.json
    # echo "COMPILED_DIR: $COMPILED_DIR"
}

start_product_service() {
    java -cp "$COMPILED_DIR/ProductService" ProductService config.json
}

start_order_service() {
    java -cp "$COMPILED_DIR/OrderService" OrderService config.json
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
