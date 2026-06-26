const { Client } = require('pg');

async function main() {
  const connectionString = 'postgresql://neondb_owner:npg_Ob7cIU2MKjEf@ep-long-fire-aonicy5s-pooler.c-2.ap-southeast-1.aws.neon.tech/neondb';
  const client = new Client({
    connectionString,
    ssl: { rejectUnauthorized: false }
  });

  await client.connect();
  console.log('Connected to database.');

  try {
    const res = await client.query(`
      SELECT column_name, data_type 
      FROM information_schema.columns 
      WHERE table_name = 'email_verification_token'
    `);
    console.log('Columns of email_verification_token:');
    console.table(res.rows);
  } catch (err) {
    console.error('Error executing query:', err);
  } finally {
    await client.end();
  }
}

main().catch(console.error);
