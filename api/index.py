import os
import sys

# Ensure root directory is on Python path so unigrab and app can be imported
root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if root_dir not in sys.path:
    sys.path.insert(0, root_dir)

from app import app

# Vercel looks for the WSGI application callable named "app"
