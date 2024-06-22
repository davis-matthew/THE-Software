# THE-ProgrammingLanguage
The THE programming language is the programming language.  Currently in work.
#### Philosophy
- Everything that can be compiled should be compiled
- Compile-time memory management
- Remove confusing pointer nonsense where possible
- Like C, but with some issues fixed
- Fast
#### Example
```c
int arr[] = {2, 3, 4, 10, 40}
int x = 10
int result = binarySearch(arr, x)
if result == -1
	print("Element not present")
else
	print("Element found at index " + result)
]

// Search for element 'x' in array 'arr'
int binarySearch(int[] arr, int x)
	int l = 0
	int r = #arr - 1
	while l <= r
		int m = l + (r - l) / 2
		
		if arr[m] = x
			return m
		]
		
		if arr[m] < x
			l = m + 1
		else
			r = m - 1
		]
	]
	return -1
]
```


# THE-Parser
THE-Parser is a grammar parser (in work).
