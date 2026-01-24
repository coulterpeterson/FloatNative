import sqlite3
import sys
import os
import json
import re

def convert_bool(val):
    return 'TRUE' if val else 'FALSE'

def escape_string(val):
    if val is None:
        return 'NULL'
    # Escape single quotes for SQL
    return "'" + str(val).replace("'", "''") + "'"

def format_array(val):
    # D1 stores as comma separated string or empty string
    if not val:
        return "'{}'"
    # If it's a JSON string like ["a","b"]
    if val.startswith('[') and val.endswith(']'):
        try:
            arr = json.loads(val)
            # PG array format: {Val1,Val2}
            # Need to double escape text inside if needed?
            # Assuming simple IDs for now
            return "'{" + ",".join(arr) + "}'"
        except:
            pass
    # If comma separated
    parts = val.split(',')
    return "'{" + ",".join(parts) + "}'"

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 migrate_d1.py <path_to_d1_dump.sql>")
        sys.exit(1)

    dump_file = sys.argv[1]
    
    # Create valid sqlite connection
    conn = sqlite3.connect(':memory:')
    cursor = conn.cursor()

    # Read and execute the dump to populate in-memory DB
    # We might need to handle some D1 specific SQL differences if any
    print(f"Loading {dump_file} into temporary SQLite DB...")
    with open(dump_file, 'r', encoding='utf-8') as f:
        script = f.read()
        # Remove lines that might cause syntax errors in standard sqlite if any (like D1 specific pragmas)
        # But generally D1 export is standard SQL
        try:
            cursor.executescript(script)
        except Exception as e:
            print(f"Error loading dump: {e}")
            # Try to continue? Or fail?
            # Often D1 exports contain `CREATE TABLE` assertions that might fail if exists
            pass

    # Open output file
    output_file = 'postgres_import.sql'
    with open(output_file, 'w', encoding='utf-8') as out:
        out.write("-- Postgres Import Script generated from D1 Dump\n")
        out.write("BEGIN;\n\n")

        tables = ['users', 'playlists', 'fp_posts', 'qr_sessions', 'device_sessions']
        
        for table in tables:
            print(f"Processing table: {table}")
            try:
                cursor.execute(f"SELECT * FROM {table}")
                rows = cursor.fetchall()
                cols = [description[0] for description in cursor.description]
                
                for row in rows:
                    vals = []
                    for i, col in enumerate(cols):
                        val = row[i]
                        
                        # Handle specific column transformations based on known schema
                        if table == 'playlists' and col == 'video_ids':
                            vals.append(format_array(val))
                        elif table == 'playlists' and col == 'is_watch_later':
                            vals.append(convert_bool(val))
                        elif table == 'fp_posts' and col.startswith('has_') or col.startswith('is_'):
                            # all boolean flags
                            vals.append(convert_bool(val))
                        elif table == 'fp_posts' and col == 'thumbnail_url':
                             # Handle potential nulls or mismatched types
                             vals.append(escape_string(val))
                        else:
                            # Generic string/int handling
                            if isinstance(val, int):
                                vals.append(str(val))
                            elif isinstance(val, float):
                                vals.append(str(val))
                            elif val is None:
                                vals.append('NULL')
                            else:
                                vals.append(escape_string(val))
                    
                    col_names = ", ".join([f'"{c}"' for c in cols])
                    val_str = ", ".join(vals)
                    
                    # Handle ON CONFLICT for idempotency
                    pk = 'id'
                    if table == 'users': pk = 'floatplane_user_id'
                    
                    sql = f"INSERT INTO {table} ({col_names}) VALUES ({val_str}) ON CONFLICT ({pk}) DO NOTHING;\n"
                    out.write(sql)
            except Exception as e:
                print(f"Skipping table {table} (maybe doesn't exist in dump): {e}")

        out.write("\nCOMMIT;\n")
    
    print(f"Done! Created {output_file}")
    print("Run with: psql -h localhost -p 5432 -U postgres -d floatnative -f postgres_import.sql")

if __name__ == "__main__":
    main()
