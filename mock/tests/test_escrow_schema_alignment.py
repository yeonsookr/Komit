from pathlib import Path
import re
import unittest

from fastapi import FastAPI
from app.routers import escrow


class SchemaAlignmentTest(unittest.TestCase):
    def test_every_table_and_column_has_korean_comment_in_both_schemas(self):
        root = Path(__file__).resolve().parents[2]
        pg = (root/'escrow/src/main/resources/db/migration/V1__create_escrow_schema.sql').read_text(encoding='utf-8')
        erd = (root/'escrow/docs/ERDCLOUD_SCHEMA.sql').read_text(encoding='utf-8')
        def tables(sql):
            return {name: body for name,body in re.findall(r'CREATE TABLE (\w+) \((.*?)\n\)',sql,re.S)}
        pg_tables, erd_tables = tables(pg), tables(erd)
        self.assertEqual(set(pg_tables), set(erd_tables))
        for name, body in pg_tables.items():
            columns = re.findall(r'^    ([a-z_]+) (?:BIGINT|INT|SMALLINT|VARCHAR|TEXT|NUMERIC|BOOLEAN|TIMESTAMPTZ)',body,re.M)
            erd_columns = re.findall(r'^    ([a-z_]+) (?:BIGINT|INT|SMALLINT|VARCHAR|TEXT|NUMERIC|BOOLEAN|DATETIME)',erd_tables[name],re.M)
            self.assertEqual(columns, erd_columns, name)
            self.assertRegex(pg, rf"COMMENT ON TABLE {name} IS '[^']*[가-힣][^']*';")
            for column in columns:
                self.assertRegex(pg, rf"COMMENT ON COLUMN {name}\.{column} IS '[^']*[가-힣][^']*';")
                self.assertRegex(erd_tables[name], rf"    {column} [^\n]*COMMENT '[^']*[가-힣][^']*'")

    def test_header_boundaries_reject_values_beyond_storage_contract(self):
        for field, value, code in [('idempotencyKey','x'*256,'E5002'),
                                   ('clientApiKey','x'*256,'E5001'),
                                   ('userKey','x'*41,'H1009'),
                                   ('transmissionTime','1200','H1003')]:
            header=escrow.EscrowRequestHeader(**escrow._escrow_header_example('createEscrow',True))
            setattr(header,field,value)
            self.assertEqual(escrow._validate_escrow_header(header,'createEscrow',True,True)[1],code)

    def test_all_success_response_examples_match_models(self):
        app=FastAPI()
        app.include_router(escrow.router)
        for route in escrow.router.routes:
            if not getattr(route,'response_model',None):
                continue
            content=route.responses.get(200,{}).get('content',{}).get('application/json',{})
            examples=[content['example']] if 'example' in content else [v['value'] for v in content.get('examples',{}).values()]
            for example in examples:
                with self.subTest(path=route.path):
                    route.response_model.model_validate(example)
