import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

public class SecretSharing {
    
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                // Process both test cases for the assignment
                System.out.println("=== Test Case 1 ===");
                BigInteger secret1 = processFile("testcase1.json");
                System.out.println("Secret for Test Case 1: " + secret1);
                
                System.out.println("\n=== Test Case 2 ===");
                BigInteger secret2 = processFile("testcase2.json");
                System.out.println("Secret for Test Case 2: " + secret2);
            } else {
                // Process single file
                String filename = args[0];
                BigInteger secret = processFile(filename);
                System.out.println("Secret: " + secret);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static BigInteger processFile(String filename) throws IOException {
        // Read JSON input file
        String jsonContent = readFile(filename);
        SecretSharingInput input = parseJSON(jsonContent);
        
        System.out.println("n = " + input.n + ", k = " + input.k);
        
        // Decode all shares from expressions or base-encoded values
        Map<Integer, BigInteger> decodedShares = decodeShares(input.shares);
        
        // Determine if we should use combinations (for wrong share detection) or exact k shares
        BigInteger secret;
        if (input.hasWrongShares) {
            // Find the secret using all combinations of k shares (error detection mode)
            secret = findSecret(decodedShares, input.k);
        } else {
            // Use exactly k shares (assignment mode)
            List<Integer> keys = new ArrayList<>(decodedShares.keySet());
            List<Integer> selectedKeys = keys.subList(0, Math.min(input.k, keys.size()));
            System.out.println("Using shares with x-coordinates: " + selectedKeys);
            secret = lagrangeInterpolation(decodedShares, selectedKeys);
        }
        
        return secret;
    }
    
    // Read file content
    private static String readFile(String filename) throws IOException {
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();
        return content.toString();
    }
    
    // Parse JSON manually to handle both formats
    private static SecretSharingInput parseJSON(String json) {
        SecretSharingInput input = new SecretSharingInput();
        
        // Clean the JSON string - remove BOM and trim
        json = json.trim();
        if (json.startsWith("\uFEFF")) {
            json = json.substring(1);
        }
        // Remove any other potential problematic characters at start
        while (json.length() > 0 && json.charAt(0) != '{') {
            json = json.substring(1);
        }
        
        try {            
            // Check if this is assignment format by looking for the pattern
            boolean isAssignmentFormat = json.indexOf("base") >= 0 && json.indexOf("value") >= 0;
            
            if (isAssignmentFormat) {
                // Assignment format: {"keys": {"n": 4, "k": 3}, "1": {"base": "10", "value": "4"}}
                parseAssignmentFormat(json, input);
                input.hasWrongShares = false; // Assignment assumes all shares are valid
            } else {
                // Function format: {"n": 4, "k": 3, "1": "sum(1, 2)", "2": "multiply(3, 4)"}
                parseFunctionFormat(json, input);
                input.hasWrongShares = true; // Function format may have wrong shares
            }
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in JSON: " + e.getMessage());
        }
        
        return input;
    }
    
    private static void parseAssignmentFormat(String json, SecretSharingInput input) {
        // Extract n value from keys object
        Pattern nPattern = Pattern.compile("\"keys\"\\s*:\\s*\\{[^}]*\"n\"\\s*:\\s*(\\d+)");
        Matcher nMatcher = nPattern.matcher(json);
        if (nMatcher.find()) {
            input.n = Integer.parseInt(nMatcher.group(1));
        } else {
            throw new IllegalArgumentException("Missing 'n' value in keys");
        }
        
        // Extract k value from keys object
        Pattern kPattern = Pattern.compile("\"keys\"\\s*:\\s*\\{[^}]*\"k\"\\s*:\\s*(\\d+)");
        Matcher kMatcher = kPattern.matcher(json);
        if (kMatcher.find()) {
            input.k = Integer.parseInt(kMatcher.group(1));
        } else {
            throw new IllegalArgumentException("Missing 'k' value in keys");
        }
        
        // Validate n and k values
        validateNK(input.n, input.k);
        
        // Extract shares in assignment format
        input.shares = new HashMap<>();
        
        // Pattern to match share objects like "1": { "base": "10", "value": "4" }
        Pattern sharePattern = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\{\\s*\"base\"\\s*:\\s*\"(\\d+)\"\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
        Matcher shareMatcher = sharePattern.matcher(json);
        
        while (shareMatcher.find()) {
            int key = Integer.parseInt(shareMatcher.group(1));
            int base = Integer.parseInt(shareMatcher.group(2));
            String value = shareMatcher.group(3);
            
            if (key <= 0) {
                System.err.println("Warning: Invalid share key " + key + " (must be positive), skipping...");
                continue;
            }
            if (base < 2 || base > 36) {
                System.err.println("Warning: Invalid base " + base + " for share " + key + ", skipping...");
                continue;
            }
            
            // Convert base-encoded value to decimal and store as string
            try {
                BigInteger decimalValue = new BigInteger(value, base);
                input.shares.put(key, decimalValue.toString());
                System.out.println("Share " + key + ": base " + base + " value '" + value + "' = " + decimalValue);
            } catch (NumberFormatException e) {
                System.err.println("Error decoding share " + key + ": Invalid number '" + value + "' in base " + base);
            }
        }
        
        // Validate we have enough shares
        if (input.shares.size() < input.k) {
            throw new IllegalArgumentException("Not enough shares provided. Need at least " + input.k + " shares, but only " + input.shares.size() + " found");
        }
        
        System.out.println("Successfully parsed assignment format JSON: n=" + input.n + ", k=" + input.k + ", shares=" + input.shares.size());
    }
    
    private static void parseFunctionFormat(String json, SecretSharingInput input) {
        // Extract n value
        Pattern nPattern = Pattern.compile("\"n\"\\s*:\\s*(\\d+)");
        Matcher nMatcher = nPattern.matcher(json);
        if (nMatcher.find()) {
            input.n = Integer.parseInt(nMatcher.group(1));
        } else {
            throw new IllegalArgumentException("Missing or invalid 'n' value in JSON");
        }
        
        // Extract k value
        Pattern kPattern = Pattern.compile("\"k\"\\s*:\\s*(\\d+)");
        Matcher kMatcher = kPattern.matcher(json);
        if (kMatcher.find()) {
            input.k = Integer.parseInt(kMatcher.group(1));
        } else {
            throw new IllegalArgumentException("Missing or invalid 'k' value in JSON");
        }
        
        // Validate n and k values
        validateNK(input.n, input.k);
        
        // Extract shares in function format
        input.shares = new HashMap<>();
        Pattern sharePattern = Pattern.compile("\"(\\d+)\"\\s*:\\s*\"([^\"]+)\"");
        Matcher shareMatcher = sharePattern.matcher(json);
        while (shareMatcher.find()) {
            int key = Integer.parseInt(shareMatcher.group(1));
            String value = shareMatcher.group(2);
            if (key <= 0) {
                System.err.println("Warning: Invalid share key " + key + " (must be positive), skipping...");
                continue;
            }
            input.shares.put(key, value);
        }
        
        // Validate we have enough shares
        if (input.shares.size() < input.k) {
            throw new IllegalArgumentException("Not enough shares provided. Need at least " + input.k + " shares, but only " + input.shares.size() + " found");
        }
        
        System.out.println("Successfully parsed function format JSON: n=" + input.n + ", k=" + input.k + ", shares=" + input.shares.size());
    }
    
    private static void validateNK(int n, int k) {
        if (n <= 0 || k <= 0) {
            throw new IllegalArgumentException("n and k must be positive integers");
        }
        if (k > n) {
            throw new IllegalArgumentException("k cannot be greater than n");
        }
        if (k < 2) {
            throw new IllegalArgumentException("k must be at least 2 for meaningful secret sharing");
        }
    }
    
    // Decode share values (handles both function expressions and direct decimal values)
    private static Map<Integer, BigInteger> decodeShares(Map<Integer, String> shares) {
        Map<Integer, BigInteger> decoded = new HashMap<>();
        
        for (Map.Entry<Integer, String> entry : shares.entrySet()) {
            try {
                String expression = entry.getValue();
                if (expression == null || expression.trim().isEmpty()) {
                    System.err.println("Warning: Empty expression for share " + entry.getKey() + ", skipping...");
                    continue;
                }
                
                BigInteger value;
                
                // Check if it's a direct number (assignment format) or function expression
                if (isDirectNumber(expression)) {
                    value = new BigInteger(expression.trim());
                } else {
                    // Function expression format
                    value = evaluateExpression(expression);
                }
                
                if (value == null) {
                    System.err.println("Warning: Could not evaluate expression for share " + entry.getKey() + ": " + expression);
                    continue;
                }
                
                decoded.put(entry.getKey(), value);
                
            } catch (Exception e) {
                System.err.println("Error decoding share " + entry.getKey() + ": " + e.getMessage());
                // Continue with other shares instead of failing completely
            }
        }
        
        return decoded;
    }
    
    // Check if a string is a direct number (no function calls)
    private static boolean isDirectNumber(String str) {
        str = str.trim();
        try {
            new BigInteger(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    // Evaluate mathematical expressions (sum, multiply, lcm, gcd, etc.)
    private static BigInteger evaluateExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }
        
        expression = expression.trim();
        
        try {
            // Handle sum(a, b, ...)
            if (expression.startsWith("sum(")) {
                List<BigInteger> numbers = extractNumbers(expression);
                if (numbers.isEmpty()) {
                    throw new IllegalArgumentException("sum() requires at least one argument");
                }
                BigInteger result = BigInteger.ZERO;
                for (BigInteger num : numbers) {
                    result = result.add(num);
                }
                return result;
            }
            
            // Handle multiply(a, b, ...)
            if (expression.startsWith("multiply(") || expression.startsWith("mul(")) {
                List<BigInteger> numbers = extractNumbers(expression);
                if (numbers.isEmpty()) {
                    throw new IllegalArgumentException("multiply() requires at least one argument");
                }
                BigInteger result = BigInteger.ONE;
                for (BigInteger num : numbers) {
                    result = result.multiply(num);
                }
                return result;
            }
            
            // Handle lcm(a, b)
            if (expression.startsWith("lcm(")) {
                List<BigInteger> numbers = extractNumbers(expression);
                if (numbers.size() < 2) {
                    throw new IllegalArgumentException("lcm() requires at least two arguments");
                }
                BigInteger result = numbers.get(0);
                for (int i = 1; i < numbers.size(); i++) {
                    result = lcm(result, numbers.get(i));
                }
                return result;
            }
            
            // Handle gcd(a, b) or hcf(a, b)
            if (expression.startsWith("gcd(") || expression.startsWith("hcf(")) {
                List<BigInteger> numbers = extractNumbers(expression);
                if (numbers.size() < 2) {
                    throw new IllegalArgumentException("gcd()/hcf() requires at least two arguments");
                }
                BigInteger result = numbers.get(0);
                for (int i = 1; i < numbers.size(); i++) {
                    result = gcd(result, numbers.get(i));
                }
                return result;
            }
            
            // Handle divide(a, b)
            if (expression.startsWith("divide(") || expression.startsWith("div(")) {
                List<BigInteger> numbers = extractNumbers(expression);
                if (numbers.size() != 2) {
                    throw new IllegalArgumentException("divide() requires exactly two arguments");
                }
                if (numbers.get(1).equals(BigInteger.ZERO)) {
                    throw new ArithmeticException("Division by zero");
                }
                return numbers.get(0).divide(numbers.get(1));
            }
            
            // Handle subtract(a, b) or sub(a, b)
            if (expression.startsWith("subtract(") || expression.startsWith("sub(")) {
                List<BigInteger> numbers = extractNumbers(expression);
                if (numbers.size() != 2) {
                    throw new IllegalArgumentException("subtract() requires exactly two arguments");
                }
                return numbers.get(0).subtract(numbers.get(1));
            }
            
            // If it's just a number
            return new BigInteger(expression);
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in expression: " + expression);
        } catch (Exception e) {
            throw new RuntimeException("Error evaluating expression '" + expression + "': " + e.getMessage());
        }
    }
    
    // Extract numbers from function expressions
    private static List<BigInteger> extractNumbers(String expression) {
        List<BigInteger> numbers = new ArrayList<>();
        
        try {
            // Find content between parentheses
            int start = expression.indexOf('(');
            int end = expression.lastIndexOf(')');
            
            if (start == -1 || end == -1 || start >= end) {
                throw new IllegalArgumentException("Invalid function format: " + expression);
            }
            
            String content = expression.substring(start + 1, end).trim();
            
            if (content.isEmpty()) {
                throw new IllegalArgumentException("Empty function arguments: " + expression);
            }
            
            // Split by comma and parse numbers
            String[] parts = content.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) {
                    continue; // Skip empty parts
                }
                try {
                    numbers.add(new BigInteger(part));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid number '" + part + "' in expression: " + expression);
                }
            }
            
            if (numbers.isEmpty()) {
                throw new IllegalArgumentException("No valid numbers found in expression: " + expression);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Error extracting numbers from expression '" + expression + "': " + e.getMessage());
        }
        
        return numbers;
    }
    
    // Calculate LCM of two BigIntegers
    private static BigInteger lcm(BigInteger a, BigInteger b) {
        return a.multiply(b).divide(gcd(a, b));
    }
    
    // Calculate GCD of two BigIntegers
    private static BigInteger gcd(BigInteger a, BigInteger b) {
        return a.gcd(b);
    }
    
    // Find secret using Lagrange interpolation with error detection
    private static BigInteger findSecret(Map<Integer, BigInteger> shares, int k) {
        List<Integer> keys = new ArrayList<>(shares.keySet());
        
        // Validate we have enough shares after decoding
        if (shares.size() < k) {
            throw new IllegalArgumentException("Not enough valid shares after decoding. Need " + k + " but only have " + shares.size());
        }
        
        Map<BigInteger, Integer> secretCounts = new HashMap<>();
        Map<BigInteger, List<List<Integer>>> secretCombinations = new HashMap<>();
        
        // Try all combinations of k shares from available shares
        List<List<Integer>> combinations = generateCombinations(keys, k);
        System.out.println("Testing " + combinations.size() + " combinations of " + k + " shares from " + shares.size() + " available shares...");
        
        int validCombinations = 0;
        for (List<Integer> combination : combinations) {
            try {
                BigInteger secret = lagrangeInterpolation(shares, combination);
                secretCounts.put(secret, secretCounts.getOrDefault(secret, 0) + 1);
                
                if (!secretCombinations.containsKey(secret)) {
                    secretCombinations.put(secret, new ArrayList<>());
                }
                secretCombinations.get(secret).add(combination);
                validCombinations++;
            } catch (Exception e) {
                System.err.println("Invalid combination " + combination + ": " + e.getMessage());
                continue;
            }
        }
        
        if (validCombinations == 0) {
            throw new RuntimeException("No valid combinations found. All share combinations failed interpolation.");
        }
        
        // Find the most frequent secret (correct one)
        BigInteger correctSecret = null;
        int maxCount = 0;
        
        for (Map.Entry<BigInteger, Integer> entry : secretCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                correctSecret = entry.getKey();
            }
        }
        
        if (correctSecret == null) {
            throw new RuntimeException("Could not determine correct secret from combinations");
        }
        
        System.out.println("Secret found with " + maxCount + " occurrences out of " + validCombinations + " valid combinations");
        
        // Identify wrong shares
        Set<Integer> validShares = new HashSet<>();
        for (List<Integer> validCombination : secretCombinations.get(correctSecret)) {
            validShares.addAll(validCombination);
        }
        
        Set<Integer> wrongShares = new HashSet<>(keys);
        wrongShares.removeAll(validShares);
        
        if (!wrongShares.isEmpty()) {
            System.out.println("Wrong shares detected: " + wrongShares);
        } else {
            System.out.println("No wrong shares detected - all shares are valid");
        }
        System.out.println("Valid shares: " + validShares);
        
        return correctSecret;
    }
    
    // Generate all combinations of k elements from list
    private static List<List<Integer>> generateCombinations(List<Integer> list, int k) {
        List<List<Integer>> combinations = new ArrayList<>();
        generateCombinationsHelper(list, k, 0, new ArrayList<>(), combinations);
        return combinations;
    }
    
    private static void generateCombinationsHelper(List<Integer> list, int k, int start, 
                                                 List<Integer> current, List<List<Integer>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            generateCombinationsHelper(list, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
    
    // Lagrange interpolation to find polynomial constant term (secret)
    private static BigInteger lagrangeInterpolation(Map<Integer, BigInteger> shares, List<Integer> keys) {
        if (keys.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 points for interpolation");
        }
        
        BigInteger secret = BigInteger.ZERO;
        
        // Calculate f(0) using Lagrange interpolation
        for (int i = 0; i < keys.size(); i++) {
            int xi = keys.get(i);
            BigInteger yi = shares.get(xi);
            
            if (yi == null) {
                throw new IllegalArgumentException("Missing share value for key " + xi);
            }
            
            // Calculate Lagrange basis polynomial Li(0)
            BigInteger numerator = BigInteger.ONE;
            BigInteger denominator = BigInteger.ONE;
            
            for (int j = 0; j < keys.size(); j++) {
                if (i != j) {
                    int xj = keys.get(j);
                    if (xi == xj) {
                        throw new IllegalArgumentException("Duplicate x-values not allowed: " + xi);
                    }
                    numerator = numerator.multiply(BigInteger.valueOf(-xj));
                    denominator = denominator.multiply(BigInteger.valueOf(xi - xj));
                }
            }
            
            if (denominator.equals(BigInteger.ZERO)) {
                throw new ArithmeticException("Division by zero in Lagrange interpolation");
            }
            
            // Add yi * Li(0) to result
            secret = secret.add(yi.multiply(numerator).divide(denominator));
        }
        
        return secret;
    }
    
    // Helper class to store parsed JSON input
    static class SecretSharingInput {
        int n;
        int k;
        Map<Integer, String> shares;
        boolean hasWrongShares; // True for function format (may have wrong shares), false for assignment format
    }
}
