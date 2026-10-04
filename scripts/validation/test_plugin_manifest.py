#!/usr/bin/env python3
"""Ephemeral keys exist only in memory in this unit test; no production manifest is emitted."""
import unittest, importlib.util,base64
from pathlib import Path
from cryptography.hazmat.primitives.asymmetric import rsa,padding
from cryptography.hazmat.primitives import hashes
from cryptography.exceptions import InvalidSignature
spec=importlib.util.spec_from_file_location('manifest',Path(__file__).parents[1]/'plugin-release-manifest.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class SigningTests(unittest.TestCase):
 def test_signature_and_tamper(self):
  key=rsa.generate_private_key(public_exponent=65537,key_size=3072);e=m.envelope({'publisher':'unit-test-only'},key);raw=base64.b64decode(e['payload']);sig=base64.b64decode(e['signature']);key.public_key().verify(sig,raw,padding.PKCS1v15(),hashes.SHA256())
  with self.assertRaises(InvalidSignature):key.public_key().verify(sig,raw+b' ',padding.PKCS1v15(),hashes.SHA256())
 def test_small_key_rejected(self):
  with self.assertRaises(ValueError):m.envelope({'publisher':'unit-test-only'},rsa.generate_private_key(public_exponent=65537,key_size=2048))
 def test_invalid_origin_rejected(self):
  for url in ['http://localhost/a','https://user:pass@localhost/a','https://localhost:99/a','https://localhost/a#part']:
   with self.assertRaises(ValueError):m.https(url)
if __name__=='__main__':unittest.main()
