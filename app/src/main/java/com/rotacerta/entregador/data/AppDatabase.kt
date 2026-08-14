package com.rotacerta.entregador.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration

class Converters {
    @TypeConverter
    fun fromPriority(value: Priority): String = value.name

    @TypeConverter
    fun toPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter
    fun fromStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)
}

@Database(entities = [Delivery::class, HistoryEntry::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deliveryDao(): DeliveryDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rotacerta.db"
                )
                    // IMPORTANTE: NÃO usar fallbackToDestructiveMigration() aqui — ele apaga
                    // silenciosamente TODAS as entregas e o histórico de ganhos do usuário
                    // sempre que a versão do banco mudar sem uma Migration explícita
                    // correspondente. Daqui pra frente, toda mudança de schema (nova coluna,
                    // nova tabela, etc.) precisa: (1) subir o `version` acima e (2) adicionar
                    // uma Migration na lista abaixo, com o SQL exato da mudança. Se esquecer,
                    // o app vai travar ao abrir em vez de apagar os dados sem avisar — o que é
                    // preferível: um crash a gente percebe e corrige, dado perdido não volta.
                    //
                    // `fallbackToDestructiveMigrationOnDowngrade` cobre só o caso raro de
                    // instalar uma versão mais antiga do app por cima de uma mais nova
                    // (ex.: testes), onde não existe migração "pra trás" possível.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        // Nenhuma migração ainda: o schema atual (v3) é o baseline a partir de agora.
        // Exemplo de como adicionar uma no futuro, ao subir pra v4:
        //
        // val MIGRATION_3_4 = object : Migration(3, 4) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE deliveries ADD COLUMN observacao TEXT NOT NULL DEFAULT ''")
        //     }
        // }
        // E então: version = 4 lá em cima, e MIGRATIONS = arrayOf(MIGRATION_3_4)
        private val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
