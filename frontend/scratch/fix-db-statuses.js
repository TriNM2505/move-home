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
    const res1 = await client.query(`
      UPDATE service_order 
      SET status = 'PENDING' 
      WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ASSIGNED')
    `);
    console.log('Updated pending payment/confirmed/assigned orders:', res1.rowCount);

    const res2 = await client.query(`
      UPDATE service_order 
      SET status = 'IN_PROGRESS' 
      WHERE status = 'AWAITING_FINAL_PAYMENT'
    `);
    console.log('Updated awaiting final payment orders:', res2.rowCount);

    const res3 = await client.query(`
      UPDATE service_order 
      SET status = 'DISPUTED' 
      WHERE status = 'IN_DISPUTE'
    `);
    console.log('Updated in dispute orders:', res3.rowCount);

  } catch (err) {
    console.error('Error executing query:', err);
  } finally {
    await client.end();
  }
}

main().catch(console.error);
