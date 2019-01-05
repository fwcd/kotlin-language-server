import sys
import re

alphanum_pattern = re.compile(r"(\d+)|(\D+)") 

def alphanum_sort_key(item):
    # Source: https://stackoverflow.com/questions/2669059/how-to-sort-alpha-numeric-set-in-python
    return tuple(int(num) if num else alpha for num, alpha in alphanum_pattern.findall(item))

def prompt_by(what, nodes, describer):
    node_dict = {describer(node): node for node in nodes}
    print(sorted(node_dict.keys(), key=alphanum_sort_key))
    print()
    choice = input("Enter a " + what + " to choose: ")
    
    if choice not in node_dict.keys():
        sys.exit("Invalid " + what + "!")
    else:
        return node_dict[choice]
