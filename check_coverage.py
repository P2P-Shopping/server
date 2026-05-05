import xml.etree.ElementTree as ET
import sys

def get_coverage(xml_file, class_name):
    tree = ET.parse(xml_file)
    root = tree.getroot()
    for package in root.findall('package'):
        for cls in package.findall('class'):
            if cls.get('name') == class_name:
                for counter in cls.findall('counter'):
                    if counter.get('type') == 'LINE':
                        missed = int(counter.get('missed'))
                        covered = int(counter.get('covered'))
                        total = missed + covered
                        percentage = (covered / total * 100) if total > 0 else 0
                        return missed, covered, percentage
    return None

xml_path = 'build/reports/jacoco/test/jacocoTestReport.xml'
classes = [
    'com/p2ps/catalog/service/ProductResolutionService',
    'com/p2ps/lists/model/Item',
    'com/p2ps/lists/model/ShoppingList',
    'com/p2ps/ai/controller/AiController',
    'com/p2ps/ai/service/AiOrchestrationService'
]

print(f"{'Class':<50} {'Missed':<10} {'Covered':<10} {'%':<10}")
for cls in classes:
    res = get_coverage(xml_path, cls)
    if res:
        print(f"{cls:<50} {res[0]:<10} {res[1]:<10} {res[2]:.1f}%")
    else:
        print(f"{cls:<50} Not found")
