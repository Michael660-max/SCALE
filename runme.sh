#!/bin/bash

# Set the root directory
ROOT_DIR=$(pwd)
COMPILED_DIR="$ROOT_DIR/compiled"
LIB_DIR="$ROOT_DIR/lib"

# Ensure the compiled directory exists
mkdir -p "$COMPILED_DIR"

# Start PostgreSQL database using Docker Compose
# TODO: Can delay be removed?
start_postgres() {
    docker compose up -d postgres
    echo "Waiting for PostgreSQL to be ready..."
    sleep 3
    echo "PostgreSQL started at port 5431"
    echo "Remote connection: Use the server's IP address with port 5431"
}

# Access PostgreSQL shell
access_postgres() {
    docker compose exec postgres psql -U postgres
}

# Compilation function
compile() {
    # Create output directories
    mkdir -p "$COMPILED_DIR/OrderService"
    mkdir -p "$COMPILED_DIR/UserService"
    mkdir -p "$COMPILED_DIR/ProductService"
    
    # Compile Java files with lib/ in classpath
    cd "$ROOT_DIR/src/OrderService" && javac -cp "$LIB_DIR/*" -d "$COMPILED_DIR/OrderService" OrderService.java && cd "$ROOT_DIR"
    cd "$ROOT_DIR/src/UserService" && javac -cp "$LIB_DIR/*" -d "$COMPILED_DIR/UserService" *.java && cd "$ROOT_DIR"
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

# Start services
start_user_service() {
    cd "$COMPILED_DIR/UserService" && java -cp "$COMPILED_DIR/UserService;$LIB_DIR/*" UserService config.json
}

start_product_service() {
    cd "$COMPILED_DIR/ProductService" && java -cp "$COMPILED_DIR/ProductService;$LIB_DIR/*" ProductService config.json
}

start_order_service() {
    cd "$COMPILED_DIR/OrderService" && java -cp "$COMPILED_DIR/OrderService;$LIB_DIR/*" OrderService config.json
}


start_workload() {
    if [ -z "$2" ]; then
        echo "Usage: ./runme.sh -w <workload_file>"
        exit 1
    fi
    start_postgres
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
    -d)
        start_postgres
        ;;
    -psql)
        access_postgres
        ;;
    *)
        echo "Usage: ./runme.sh -c | -u | -p | -o | -w <workload_file> | -d | -psql"
        echo "  -c     Compile services"
        echo "  -u     Start user service"
        echo "  -p     Start product service"
        echo "  -o     Start order service"
        echo "  -w     Run workload (automatically starts PostgreSQL)"
        echo "  -d     Start PostgreSQL database"
        echo "  -psql  Access PostgreSQL shell"
        exit 1
        ;;
esac