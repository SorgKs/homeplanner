#!/usr/bin/env python3
"""Generate self-signed SSL certificate for development.

This script creates a self-signed certificate that can be used for HTTPS
in development. The certificate will be valid for 365 days.

Usage:
    python scripts/generate_self_signed_cert.py [output_dir]

Output:
    - cert.pem: SSL certificate
    - key.pem: Private key

For production, use certificates from a trusted CA (Let's Encrypt, etc.).
"""

import sys
from pathlib import Path
from datetime import datetime, timedelta
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa


def generate_self_signed_cert(
    output_dir: Path,
    hostname: str = "localhost",
    valid_days: int = 365,
) -> tuple[Path, Path]:
    """Generate a self-signed SSL certificate.
    
    Args:
        output_dir: Directory to save certificate files
        hostname: Hostname for the certificate (default: localhost)
        valid_days: Certificate validity period in days
        
    Returns:
        Tuple of (cert_path, key_path)
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Generate private key
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )
    
    # Create certificate
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "US"),
        x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, "Development"),
        x509.NameAttribute(NameOID.LOCALITY_NAME, "Local"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "HomePlanner Dev"),
        x509.NameAttribute(NameOID.COMMON_NAME, hostname),
    ])
    
    cert = x509.CertificateBuilder().subject_name(
        subject
    ).issuer_name(
        issuer
    ).public_key(
        private_key.public_key()
    ).serial_number(
        x509.random_serial_number()
    ).not_valid_before(
        datetime.utcnow()
    ).not_valid_after(
        datetime.utcnow() + timedelta(days=valid_days)
    ).add_extension(
        x509.SubjectAlternativeName([
            x509.DNSName(hostname),
            x509.DNSName("localhost"),
            x509.IPAddress(x509.IPv4Address("127.0.0.1")),
            x509.IPAddress(x509.IPv4Address("10.0.2.2")),  # Android emulator
        ]),
        critical=False,
    ).sign(private_key, hashes.SHA256())
    
    # Write certificate
    cert_path = output_dir / "cert.pem"
    with cert_path.open("wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))
    
    # Write private key
    key_path = output_dir / "key.pem"
    with key_path.open("wb") as f:
        f.write(private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        ))
    
    return cert_path, key_path


def main():
    """Main entry point."""
    if len(sys.argv) > 1:
        output_dir = Path(sys.argv[1])
    else:
        output_dir = Path(__file__).parent.parent / "certs"
    
    print(f"Generating self-signed certificate in {output_dir}...")
    print("This certificate is for development only!")
    print()
    
    try:
        cert_path, key_path = generate_self_signed_cert(output_dir)
        print(f"✓ Certificate created: {cert_path}")
        print(f"✓ Private key created: {key_path}")
        print()
        print("To enable HTTPS, add to common/config/settings.toml:")
        print(f'  ssl_certfile = "{cert_path.relative_to(Path.cwd())}"')
        print(f'  ssl_keyfile = "{key_path.relative_to(Path.cwd())}"')
        print()
        print("⚠️  WARNING: This is a self-signed certificate.")
        print("   Browsers and Android will show security warnings.")
        print("   For production, use certificates from a trusted CA.")
    except ImportError:
        print("ERROR: cryptography library not installed.")
        print("Install it with: pip install cryptography")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()

