#!/bin/bash

# Set the root directory
ROOT_DIR=$(pwd)
COMPILED_DIR="$ROOT_DIR/tests"

# Start services
start_all_services() {
    $ROOT_DIR/runme.sh -u &
    $ROOT_DIR/runme.sh -o &
    $ROOT_DIR/runme.sh -p &
}

start_user_service() {

    python3 "$COMPILED_DIR/UserServiceTest.py" 
}

start_product_service() {
    python3 "$COMPILED_DIR/ProductServiceTest.py"
}

start_order_service() {
    python3 "$COMPILED_DIR/OrderServiceTest.py"
}

test_product_service() {
    $ROOT_DIR/runme.sh -c
    # $ROOT_DIR/runtest.sh -restart
    $ROOT_DIR/runme.sh -p &
    python3 parser.py simple_test_product.txt
    $ROOT_DIR/runtest.sh -end
}

# Argument handling
case "$1" in
    -start)
        start_all_services
        ;;
    -restart)
        pkill -f java
        sleep .5
        start_all_services
        ;;
    -end)
        pkill -f java
        echo "ended all services"
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
    -t)
        test_product_service
        ;;
    *)
        echo "Usage: ./runtest.sh -u | -p | -o"
        exit 1
        ;;
esac
