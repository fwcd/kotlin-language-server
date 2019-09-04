import sys
import re

alphanum_pattern = re.compile(r"(\d+)|(\D+)")

def title(s, padding=2):
    length = len(s) + (2 * padding)
    print("=" * length)
    print(f"  {s}  ")
    print("=" * length)

def confirm(what):
    result = input(what + " [y/n] ")
    return result.lower().startswith("y")

def alphanum_sort_key(item):
    # Source: https://stackoverflow.com/questions/2669059/how-to-sort-alpha-numeric-set-in-python
    return tuple(int(num) if num else alpha for num, alpha in alphanum_pattern.findall(item))

def require_not_none(description, x):
    if x == None:
        sys.exit(description + " not present")

def prompt_by(what, nodes, describer, default=None):
    node_dict = {describer(node): node for node in nodes}
    sorted_described = sorted(node_dict.keys(), key=alphanum_sort_key)

    print()
    print(sorted_described)
    print()

    last_entry = sorted_described[-1] if len(sorted_described) > 0 else None
    choice = input(f"Enter a {what} to choose [default: {last_entry}]: ").strip()
    print()

    if len(choice) == 0 and last_entry:
        return node_dict[last_entry]
    elif choice not in node_dict.keys():
        return default
    else:
        return node_dict[choice]
