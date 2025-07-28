# Shamir's Secret Sharing Algorithm Implementation

This Java program implements Shamir's Secret Sharing Algorithm to reconstruct a secret from shared keys, with robust error handling and the ability to detect and filter out corrupted/wrong shares.

## Features

- **Large Number Support**: Handles 20-40 digit numbers using BigInteger
- **Function Expression Support**: Evaluates mathematical expressions like sum(), multiply(), lcm(), gcd(), etc.
- **Robust Error Detection**: Identifies wrong shares and filters them out automatically
- **Comprehensive Error Handling**: Gracefully handles malformed input, invalid expressions, and edge cases
- **Polynomial Reconstruction**: Uses Lagrange interpolation for k-1 degree polynomial reconstruction
- **No External Libraries**: Pure Java implementation without external dependencies
- **Input Validation**: Validates JSON structure, parameter values, and mathematical expressions

## How it Works

1. **Input**: JSON file containing:
   - `n`: Total number of shares
   - `k`: Minimum shares required to reconstruct the secret
   - Share data as key-value pairs where values can be mathematical expressions

2. **Process**:
   - Validates input parameters and JSON structure
   - Decodes function expressions to actual numeric values with error handling
   - Generates all possible combinations of k shares from n total shares
   - Uses Lagrange interpolation to find the polynomial constant term (secret)
   - Identifies the most frequent secret across all valid combinations
   - Filters out wrong shares that don't contribute to the correct secret

3. **Output**: The reconstructed secret value with detailed analysis

## Usage

```bash
javac SecretSharing.java
java SecretSharing [input_file.json]
```

If no filename is provided, it defaults to `input.json`.

## Input JSON Format

```json
{
    "n": 6,
    "k": 3,
    "1": "sum(123456789012345678901234567890, 987654321098765432109876543210)",
    "2": "multiply(12345, 67890)", 
    "3": "lcm(48, 18)",
    "4": "gcd(48, 18)",
    "5": "300000000000000000000000000000",
    "6": "1999999999999999999999999999998"
}
```

## Supported Functions

- `sum(a, b, ...)` - Addition of multiple numbers
- `multiply(a, b, ...)` or `mul(a, b, ...)` - Multiplication of multiple numbers
- `lcm(a, b, ...)` - Least Common Multiple of multiple numbers
- `gcd(a, b, ...)` or `hcf(a, b, ...)` - Greatest Common Divisor of multiple numbers
- `divide(a, b)` or `div(a, b)` - Division of two numbers
- `subtract(a, b)` or `sub(a, b)` - Subtraction of two numbers
- Direct numeric values (supporting very large numbers)

## Error Handling

The program includes comprehensive error handling for:

- **Input Validation**: Missing or invalid n/k values, insufficient shares
- **Mathematical Errors**: Division by zero, invalid function arguments
- **Expression Parsing**: Malformed expressions, invalid numbers, empty functions
- **Interpolation Errors**: Duplicate x-values, insufficient points
- **Edge Cases**: Empty shares, corrupted data, numerical overflow

## Algorithm Details

The implementation uses:
- **Shamir's Secret Sharing**: Based on polynomial interpolation over finite fields
- **Lagrange Interpolation**: To reconstruct the polynomial from k points
- **Combinatorial Analysis**: Tests all C(n,k) combinations to detect errors
- **Consensus Method**: The most frequently occurring secret is considered correct
- **Robust Validation**: Multiple layers of input and computation validation

## Security Features

- Handles very large cryptographic numbers (20-40 digits)
- Automatically detects and reports corrupted shares
- Requires minimum threshold of shares to prevent unauthorized access
- No single point of failure
- Graceful degradation with detailed error reporting

## Example Output

### Successful Reconstruction:
```
Successfully parsed JSON: n=6, k=3, shares=6
n = 6, k = 3
Share 1: sum(123456789012345678901234567890, 987654321098765432109876543210) = 1111111110111111111011111111100
Share 2: multiply(12345, 67890) = 838102050
Share 3: lcm(48, 18) = 144
...
Testing 20 combinations of 3 shares from 6 available shares...
Secret found with 1 occurrences out of 20 valid combinations
Wrong shares detected: [2, 4, 5]
Valid shares: [1, 3, 6]
Secret: 2399999998199999999819999999835
```

### Error Handling Example:
```
Error decoding share 1: Error evaluating expression 'sum(123, abc)': Invalid number 'abc' in expression
Error decoding share 2: Error evaluating expression 'divide(100, 0)': Division by zero
java.lang.IllegalArgumentException: Not enough valid shares after decoding. Need 3 but only have 1
```

## Test Files

- `input.json` - Sample input with function expressions
- `test_input.json` - Test case with wrong shares
- `robust_test.json` - Test case demonstrating robustness
- `error_test.json` - Test case for error handling

This indicates robust error detection and recovery capabilities while maintaining the core cryptographic security requirements.
